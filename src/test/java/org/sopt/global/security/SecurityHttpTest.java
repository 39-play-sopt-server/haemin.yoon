package org.sopt.global.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 컨트롤러에 도달하기 전에 보안 필터가 적용하는 공통 인증 정책을 검증합니다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityHttpTest {
  @Autowired MockMvc mvc;
  @Autowired AccessTokenIssuer issuer;
  @Autowired JwtProperties properties;
  @Autowired JdbcTemplate jdbc;

  @Test
  void rejectsProtectedRequestWithoutTokenUsingCommonErrorBody() throws Exception {
    jdbc.update("DELETE FROM posts");
    expectUnauthorized(
        mvc.perform(
            post("/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\"제목\",\"content\":\"본문\",\"category\":\"GENERAL\",\"memberId\":1}")));
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM posts", Integer.class));
    expectUnauthorized(mvc.perform(get("/members/me")));
    expectUnauthorized(mvc.perform(delete("/members/1")));
    expectUnauthorized(mvc.perform(put("/posts/1/likes/1")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"Bearer", "Bearer ", "Bearer not-a-jwt", "Basic dXNlcjpwYXNz", "token"})
  void rejectsMalformedAuthorizationHeader(String authorization) throws Exception {
    expectUnauthorized(
        mvc.perform(get("/members/me").header(HttpHeaders.AUTHORIZATION, authorization)));
  }

  @Test
  void rejectsTamperedForeignAndExpiredTokens() throws Exception {
    String valid = issuer.issue(1L, UUID.randomUUID());
    String[] parts = valid.split("\\.");
    String otherSubject =
        Jwts.builder().subject("2").compact().split("\\.")[1]; // 서명은 그대로 두고 payload만 교체합니다.
    String tampered = parts[0] + "." + otherSubject + "." + parts[2];

    var foreignKey = Encoders.BASE64.encode(Jwts.SIG.HS256.key().build().getEncoded());
    String foreign =
        new AccessTokenIssuer(
                new SecurityConfig().jwtKey(new JwtProperties(foreignKey, Duration.ofMinutes(30))),
                properties)
            .issue(1L, UUID.randomUUID());
    String expired =
        new AccessTokenIssuer(
                new SecurityConfig().jwtKey(properties),
                new JwtProperties(properties.secret(), Duration.ofSeconds(-1)))
            .issue(1L, UUID.randomUUID());
    String unsigned =
        Jwts.builder()
            .subject("1")
            .expiration(new Date(System.currentTimeMillis() + 60_000))
            .compact();

    for (String token : new String[] {tampered, foreign, expired, unsigned}) {
      expectUnauthorized(
          mvc.perform(get("/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)));
    }
  }

  @Test
  void allowsPublicEndpointsWithoutToken() throws Exception {
    mvc.perform(get("/posts")).andExpect(status().isOk());
    mvc.perform(head("/posts")).andExpect(status().isOk());
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    // 잘못된 토큰이 있어도 공개 API는 인증 없이 처리합니다.
    mvc.perform(get("/posts").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
        .andExpect(status().isOk());
    mvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@example.com\",\"password\":\"password1234\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void doesNotCreateSessionForAuthenticatedRequests() throws Exception {
    mvc.perform(
            get("/posts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issuer.issue(1L, UUID.randomUUID())))
        .andExpect(status().isOk())
        .andExpect(request().sessionAttributeDoesNotExist("SPRING_SECURITY_CONTEXT"))
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
  }

  private void expectUnauthorized(ResultActions result) throws Exception {
    result
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.data").isEmpty())
        .andExpect(jsonPath("$.violations").isEmpty());
  }
}
