package org.sopt.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sopt.support.MemberDatabaseSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthHttpApiTest extends MemberDatabaseSupport {
  private static final String PASSWORD = "password1234";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired JsonMapper json;

  @Test
  void signupLoginAndReadOwnMemberWithAccessToken() throws Exception {
    signup("홍길동", "Hong@Example.com", PASSWORD)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.name").value("홍길동"))
        .andExpect(jsonPath("$.data.email").value("hong@example.com"))
        .andExpect(jsonPath("$.data.password").doesNotExist());
    long id =
        jdbc.queryForObject("SELECT id FROM members WHERE email = 'hong@example.com'", Long.class);
    String stored =
        jdbc.queryForObject("SELECT password_hash FROM members WHERE id = ?", String.class, id);
    assertNotEquals(PASSWORD, stored);
    assertTrue(stored.startsWith("$2"), "BCrypt 해시로 저장합니다.");

    String token = login("HONG@example.com", PASSWORD);
    mvc.perform(get("/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(id))
        .andExpect(jsonPath("$.data.name").value("홍길동"))
        .andExpect(jsonPath("$.data.email").value("hong@example.com"));
  }

  @Test
  void loginResponseDescribesBearerToken() throws Exception {
    signup("홍길동", "hong@example.com", PASSWORD).andExpect(status().isCreated());
    mvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", "hong@example.com", "password", PASSWORD))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.data.expiresIn").value(1800));
  }

  @Test
  void rejectsDuplicateEmailIgnoringCaseWithoutWritingMember() throws Exception {
    signup("홍길동", "hong@example.com", PASSWORD).andExpect(status().isCreated());
    signup("다른 사람", "HONG@example.com", "another-password")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MEMBER_EMAIL_DUPLICATED"));
    assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM members", Integer.class));
  }

  @Test
  void unknownEmailWrongPasswordAndDeletedMemberShareTheSameFailure() throws Exception {
    signup("홍길동", "hong@example.com", PASSWORD).andExpect(status().isCreated());
    String unknown = loginFailure("nobody@example.com", PASSWORD);
    String wrongPassword = loginFailure("hong@example.com", "wrong-password");
    String malformedEmail = loginFailure("not-an-email", PASSWORD);
    assertEquals(unknown, wrongPassword);
    assertEquals(unknown, malformedEmail);

    long id =
        jdbc.queryForObject("SELECT id FROM members WHERE email = 'hong@example.com'", Long.class);
    String token = login("hong@example.com", PASSWORD);
    memberCommands.deleteMember(id);
    assertEquals(unknown, loginFailure("hong@example.com", PASSWORD));
    // 탈퇴한 회원의 세션은 인증 단계에서 거부합니다.
    mvc.perform(get("/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"name\":\"홍길동\",\"email\":\"hong@example.com\"}",
        "{\"name\":\"홍길동\",\"password\":\"password1234\"}",
        "{\"email\":\"hong@example.com\",\"password\":\"password1234\"}",
        "{\"name\":\"홍길동\",\"email\":\"not-an-email\",\"password\":\"password1234\"}",
        "{\"name\":\"홍길동\",\"email\":\"hong@example.com\",\"password\":\"short\"}"
      })
  void rejectsInvalidSignupRequestWithoutWritingMember(String request) throws Exception {
    mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM members", Integer.class));
  }

  @Test
  void validatesNameBoundariesNulAndPasswordBytes() throws Exception {
    signup("가".repeat(51), "a@example.com", PASSWORD)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.violations[0].code").value("INVALID_LENGTH"));
    signup("앞\0뒤", "b@example.com", PASSWORD)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MEMBER_NAME_NUL_NOT_ALLOWED"));
    // 25자이지만 UTF-8로 75바이트이므로 BCrypt가 일부를 무시하지 않도록 거부합니다.
    signup("홍길동", "c@example.com", "가".repeat(25))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_PASSWORD"));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM members", Integer.class));

    String name = "😀".repeat(25);
    signup(name, "d@example.com", "가".repeat(24))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.name").value(name));
    login("d@example.com", "가".repeat(24));
  }

  @Test
  void documentsAuthEndpoints() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
        .andExpect(
            jsonPath("$.paths['/auth/signup'].post.responses['409'].$ref")
                .value("#/components/responses/MemberEmailDuplicated"))
        .andExpect(
            jsonPath("$.paths['/auth/login'].post.responses['401'].$ref")
                .value("#/components/responses/InvalidCredentials"))
        .andExpect(jsonPath("$.paths['/auth/login'].post.security").doesNotExist())
        .andExpect(jsonPath("$.paths['/posts'].post.security[0].bearerAuth").exists())
        .andExpect(jsonPath("$.paths['/posts'].get.security").doesNotExist())
        .andExpect(
            jsonPath("$.components.schemas.SignupRequest.properties.name.maxLength").value(50));
  }

  @Test
  void rotatesRefreshCookieAndRevokesOnlyTheLoggedOutSession() throws Exception {
    signup("회원", "session@example.com", PASSWORD).andExpect(status().isCreated());
    var first = loginResponse("session@example.com");
    var other = loginResponse("session@example.com");
    var cookie = first.getCookie("refreshToken");
    assertNotNull(cookie);
    assertTrue(cookie.isHttpOnly());
    assertTrue(cookie.getSecure());
    assertEquals("/auth", cookie.getPath());
    assertEquals("Strict", cookie.getAttribute("SameSite"));
    assertFalse(first.getContentAsString().contains(cookie.getValue()));
    mvc.perform(post("/auth/refresh").cookie(cookie)).andExpect(status().isForbidden());
    var renewed = refresh(cookie).andExpect(status().isOk()).andReturn().getResponse();
    refresh(cookie).andExpect(status().isUnauthorized());
    var access = renewed.getHeader(HttpHeaders.AUTHORIZATION);
    assertNotEquals(first.getHeader(HttpHeaders.AUTHORIZATION), access);
    mvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, access))
        .andExpect(status().isOk())
        .andExpect(cookie().maxAge("refreshToken", 0));
    me(access).andExpect(status().isUnauthorized());
    me(first.getHeader(HttpHeaders.AUTHORIZATION)).andExpect(status().isUnauthorized());
    refresh(renewed.getCookie("refreshToken")).andExpect(status().isUnauthorized());
    me(other.getHeader(HttpHeaders.AUTHORIZATION)).andExpect(status().isOk());
  }

  @Test
  void enforcesRoleAndOwnershipAndInvalidatesEverySessionOnWithdrawal() throws Exception {
    signup("회원", "user@example.com", PASSWORD).andExpect(status().isCreated());
    var first = loginResponse("user@example.com");
    var other = loginResponse("user@example.com");
    String access = first.getHeader(HttpHeaders.AUTHORIZATION);
    long id =
        jdbc.queryForObject("select id from members where email='user@example.com'", Long.class);
    mvc.perform(get("/members")).andExpect(status().isUnauthorized());
    mvc.perform(get("/members").header(HttpHeaders.AUTHORIZATION, access))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    mvc.perform(delete("/members/1").header(HttpHeaders.AUTHORIZATION, access))
        .andExpect(status().isForbidden());
    mvc.perform(put("/posts/1/likes/1").header(HttpHeaders.AUTHORIZATION, access))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/posts")
                .header(HttpHeaders.AUTHORIZATION, access)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\"제목\",\"content\":\"내용\",\"category\":\"GENERAL\",\"memberId\":1}"))
        .andExpect(status().isForbidden());
    mvc.perform(delete("/posts/987654").header(HttpHeaders.AUTHORIZATION, access))
        .andExpect(status().isNotFound());
    jdbc.update("update members set role='ROLE_ADMIN' where id=?", id);
    mvc.perform(get("/members").header(HttpHeaders.AUTHORIZATION, access))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[1].id").value(id))
        .andExpect(jsonPath("$.data[1].passwordHash").doesNotExist());
    jdbc.update("update members set role='ROLE_USER' where id=?", id);
    mvc.perform(delete("/members/{id}", id).header(HttpHeaders.AUTHORIZATION, access))
        .andExpect(status().isOk());
    me(access).andExpect(status().isUnauthorized());
    me(other.getHeader(HttpHeaders.AUTHORIZATION)).andExpect(status().isUnauthorized());
    refresh(other.getCookie("refreshToken")).andExpect(status().isUnauthorized());
  }

  @Test
  void onlyOneConcurrentRefreshSucceedsAndSessionExpiryRejectsBothTokens() throws Exception {
    signup("회원", "race@example.com", PASSWORD).andExpect(status().isCreated());
    var login = loginResponse("race@example.com");
    var original = login.getCookie("refreshToken");
    var start = new java.util.concurrent.CyclicBarrier(2);
    java.util.concurrent.Callable<Integer> renew =
        () -> {
          start.await(5, java.util.concurrent.TimeUnit.SECONDS);
          return refresh(original).andReturn().getResponse().getStatus();
        };
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var first = pool.submit(renew);
      var second = pool.submit(renew);
      assertEquals(
          java.util.Set.of(200, 401),
          java.util.Set.copyOf(java.util.List.of(first.get(), second.get())));
    }
    var next = loginResponse("race@example.com");
    jdbc.update(
        "update login_sessions set expires_at=?",
        java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)));
    refresh(next.getCookie("refreshToken")).andExpect(status().isUnauthorized());
    me(next.getHeader(HttpHeaders.AUTHORIZATION)).andExpect(status().isUnauthorized());
  }

  private ResultActions me(String authorization) throws Exception {
    return mvc.perform(get("/members/me").header(HttpHeaders.AUTHORIZATION, authorization));
  }

  private ResultActions refresh(Cookie cookie) throws Exception {
    return mvc.perform(post("/auth/refresh").cookie(cookie).header("X-Refresh-Request", "1"));
  }

  private MockHttpServletResponse loginResponse(String email) throws Exception {
    return mvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", email, "password", PASSWORD))))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse();
  }

  private ResultActions signup(String name, String email, String password) throws Exception {
    return mvc.perform(
        post("/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("name", name, "email", email, "password", password))));
  }

  private String login(String email, String password) throws Exception {
    var response =
        mvc.perform(
                post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(Map.of("email", email, "password", password))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    return json.readTree(response.getContentAsString())
        .get("data")
        .get("accessToken")
        .stringValue();
  }

  private String loginFailure(String email, String password) throws Exception {
    return mvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", email, "password", password))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String body(Map<String, String> values) {
    return json.writeValueAsString(values);
  }
}
