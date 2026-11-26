package org.sopt.global.security;

import static org.junit.jupiter.api.Assertions.*;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.WeakKeyException;
import java.time.Duration;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class AccessTokenTest {
  private static final String SECRET =
      Encoders.BASE64.encode(Jwts.SIG.HS256.key().build().getEncoded());

  private final SecretKey key =
      new SecurityConfig().jwtKey(new JwtProperties(SECRET, Duration.ofMinutes(30)));

  @Test
  void issuesTokenWhoseSubjectIsMemberId() {
    var provider = new AccessTokenIssuer(key, new JwtProperties(SECRET, Duration.ofMinutes(30)));
    assertEquals(
        42L, new AccessTokenReader(key).read(provider.issue(42L, UUID.randomUUID())).memberId());
  }

  @Test
  void rejectsExpiredToken() {
    var provider = new AccessTokenIssuer(key, new JwtProperties(SECRET, Duration.ofSeconds(-1)));
    String token = provider.issue(1L, UUID.randomUUID());
    assertThrows(ExpiredJwtException.class, () -> new AccessTokenReader(key).read(token));
  }

  @Test
  void failsFastWithoutSecretOrWithKeyShorterThan256Bits() {
    assertThrows(
        IllegalStateException.class,
        () -> new SecurityConfig().jwtKey(new JwtProperties(" ", Duration.ofMinutes(30))));
    String weak = Encoders.BASE64.encode(new byte[31]);
    assertThrows(
        WeakKeyException.class,
        () -> new SecurityConfig().jwtKey(new JwtProperties(weak, Duration.ofMinutes(30))));
  }
}
