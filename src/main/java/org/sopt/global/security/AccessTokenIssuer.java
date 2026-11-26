package org.sopt.global.security;

import io.jsonwebtoken.Jwts;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccessTokenIssuer {
  private final SecretKey key;
  private final JwtProperties properties;

  public String issue(long memberId, UUID sessionId) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(Long.toString(memberId))
        .id(UUID.randomUUID().toString())
        .claim("sid", sessionId.toString())
        .claim("token_use", "access")
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(properties.accessTokenExpiration())))
        .signWith(key, Jwts.SIG.HS256)
        .compact();
  }
}
