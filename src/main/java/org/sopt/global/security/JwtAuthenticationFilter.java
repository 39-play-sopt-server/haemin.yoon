package org.sopt.global.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization 헤더의 Bearer 토큰이 유효하면 회원 ID를 principal로 SecurityContext에 저장합니다. 토큰이 없거나 유효하지 않으면 인증
 * 정보 없이 다음 필터로 넘기고, 인증이 필요한 요청은 인가 단계에서 401로 거부됩니다.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private static final String BEARER_PREFIX = "Bearer ";

  private final AccessTokenReader reader;
  private final SessionAuthority sessions;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String token = resolveToken(request);
    if (token != null) {
      try {
        var identity = reader.read(token);
        sessions
            .currentRole(identity)
            .ifPresent(
                role -> {
                  var authentication =
                      UsernamePasswordAuthenticationToken.authenticated(
                          identity.memberId(), null, List.of(new SimpleGrantedAuthority(role)));
                  authentication.setDetails(identity);
                  SecurityContextHolder.getContext().setAuthentication(authentication);
                });
      } catch (JwtException | IllegalArgumentException invalidToken) {
        SecurityContextHolder.clearContext();
      }
    }
    filterChain.doFilter(request, response);
  }

  private String resolveToken(HttpServletRequest request) {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith(BEARER_PREFIX)) {
      return header.substring(BEARER_PREFIX.length());
    }
    return null;
  }
}
