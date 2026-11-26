package org.sopt.global.security;

import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class AccessTokenReader {
  private final JwtParser parser;

  public AccessTokenReader(SecretKey key) {
    parser = Jwts.parser().verifyWith(key).require("token_use", "access").build();
  }

  public AccessIdentity read(String token) {
    var claims = parser.parseSignedClaims(token).getPayload();
    String session = claims.get("sid", String.class);
    if (session == null || claims.getExpiration() == null)
      throw new IllegalArgumentException("필수 claim 누락");
    return new AccessIdentity(Long.parseLong(claims.getSubject()), UUID.fromString(session));
  }
}
