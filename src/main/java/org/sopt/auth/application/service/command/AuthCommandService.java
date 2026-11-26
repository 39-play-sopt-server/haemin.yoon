package org.sopt.auth.application.service.command;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.sopt.auth.application.port.in.command.AuthCommandUseCase;
import org.sopt.auth.application.port.in.command.dto.LoginResult;
import org.sopt.auth.application.port.in.command.dto.SignupResult;
import org.sopt.auth.application.port.out.LoginSessions;
import org.sopt.auth.domain.PasswordPolicy;
import org.sopt.auth.domain.exception.AuthErrorCode;
import org.sopt.auth.domain.exception.AuthException;
import org.sopt.global.exception.BaseException;
import org.sopt.global.exception.GlobalErrorCode;
import org.sopt.global.security.AccessIdentity;
import org.sopt.global.security.AccessTokenIssuer;
import org.sopt.global.security.JwtProperties;
import org.sopt.member.application.port.in.command.MemberCommandUseCase;
import org.sopt.member.application.port.in.query.MemberQueryUseCase;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 자격 증명을 확인하고 로그인 세션과 토큰의 수명 주기를 관리합니다. */
@Service
public class AuthCommandService implements AuthCommandUseCase {
  private final MemberCommandUseCase memberCommands;
  private final MemberQueryUseCase memberQueries;
  private final PasswordEncoder passwordEncoder;
  private final AccessTokenIssuer issuer;
  private final JwtProperties properties;
  private final LoginSessions sessions;
  private final SecureRandom random = new SecureRandom();
  private static final Duration SESSION_LIFETIME = Duration.ofDays(14);

  /** 존재하지 않는 이메일도 같은 비용으로 비교해 응답 시간으로 가입 여부를 추측하기 어렵게 합니다. */
  private final String unknownMemberHash;

  public AuthCommandService(
      MemberCommandUseCase memberCommands,
      MemberQueryUseCase memberQueries,
      PasswordEncoder passwordEncoder,
      AccessTokenIssuer issuer,
      JwtProperties properties,
      LoginSessions sessions) {
    this.memberCommands = memberCommands;
    this.memberQueries = memberQueries;
    this.passwordEncoder = passwordEncoder;
    this.issuer = issuer;
    this.properties = properties;
    this.sessions = sessions;
    this.unknownMemberHash = passwordEncoder.encode("unknown-member-password");
  }

  @Override
  public SignupResult signup(String name, String email, String rawPassword) {
    PasswordPolicy.validate(rawPassword);
    var member = memberCommands.registerMember(name, email, passwordEncoder.encode(rawPassword));
    return new SignupResult(member.id(), member.name(), member.email());
  }

  @Override
  @Transactional
  public LoginResult login(String email, String rawPassword) {
    var credential = memberQueries.findActiveCredentialByEmail(email);
    String hash = credential.map(found -> found.passwordHash()).orElse(unknownMemberHash);
    boolean matches =
        rawPassword != null
            && rawPassword.getBytes(StandardCharsets.UTF_8).length <= PasswordPolicy.MAX_BYTES
            && passwordEncoder.matches(rawPassword, hash);
    if (credential.isEmpty() || !matches) {
      throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
    }
    var identity = new AccessIdentity(credential.get().id(), UUID.randomUUID());
    String refresh = newRefreshToken();
    sessions.open(identity, hash(refresh), Instant.now().plus(SESSION_LIFETIME));
    return tokens(identity, refresh, SESSION_LIFETIME.toSeconds());
  }

  @Override
  @Transactional
  public LoginResult refresh(String token) {
    if (token == null || token.length() != 43)
      throw new BaseException(GlobalErrorCode.UNAUTHORIZED);
    String next = newRefreshToken();
    var session =
        sessions
            .rotate(hash(token), hash(next))
            .orElseThrow(() -> new BaseException(GlobalErrorCode.UNAUTHORIZED));
    return tokens(
        session.identity(), next, Duration.between(Instant.now(), session.expiry()).toSeconds());
  }

  public void logout(UUID sessionId) {
    sessions.close(sessionId);
  }

  private LoginResult tokens(AccessIdentity identity, String refresh, long refreshLifetime) {
    return new LoginResult(
        issuer.issue(identity.memberId(), identity.sessionId()),
        properties.accessTokenExpiration().toSeconds(),
        refresh,
        refreshLifetime);
  }

  private String newRefreshToken() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(token.getBytes(StandardCharsets.US_ASCII)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
