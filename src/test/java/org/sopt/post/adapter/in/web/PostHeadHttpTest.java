package org.sopt.post.adapter.in.web;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sopt.post.application.port.in.command.PostCommandUseCase;
import org.sopt.post.domain.Category;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** 실제 서블릿 컨테이너의 HEAD 본문 생략과 조회수 비증가 계약을 함께 검증합니다. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties =
        "spring.datasource.url=jdbc:h2:mem:post-head-http;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class PostHeadHttpTest {
  @LocalServerPort int port;
  @Autowired PostCommandUseCase commands;
  @Autowired JdbcTemplate jdbc;
  private final HttpClient client = HttpClient.newHttpClient();

  @BeforeEach
  void clearPosts() {
    jdbc.update("DELETE FROM posts");
  }

  @AfterEach
  void closeClient() {
    client.close();
  }

  @Test
  void headDetailReturnsHeadersWithoutIncrementingViewCount() throws Exception {
    long id = createStoredPost();

    for (int i = 0; i < 2; i++) {
      var response = head(id, null);
      assertEquals(200, response.statusCode());
      assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
      assertTrue(
          response
              .headers()
              .firstValue("Content-Type")
              .orElseThrow()
              .startsWith("application/json"));
      assertEquals("", response.body());
    }
    assertEquals(0L, storedCount(id));

    var get = client.send(request(id).GET().build(), HttpResponse.BodyHandlers.ofString());
    assertEquals(200, get.statusCode());
    assertEquals(1L, storedCount(id));
    assertEquals(200, head(id, "application/json").statusCode());
    assertEquals(1L, storedCount(id));
  }

  @Test
  void detailGetDisablesCachingAndCountsRepeatedRequests() throws Exception {
    long id = createStoredPost();

    for (long count = 1; count <= 2; count++) {
      var response = client.send(request(id).GET().build(), HttpResponse.BodyHandlers.ofString());
      assertEquals(200, response.statusCode());
      assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
      assertEquals(count, storedCount(id));
    }
  }

  @Test
  void headMissingPostReturns404WithoutIncrementingOtherPosts() throws Exception {
    long id = createStoredPost();

    var response = head(Long.MAX_VALUE, null);
    assertEquals(404, response.statusCode());
    assertEquals("", response.body());
    assertEquals(0L, storedCount(id));
  }

  @ParameterizedTest
  @ValueSource(strings = {"application/xml", "text/html"})
  void unsupportedHeadAcceptDoesNotIncrementViewCount(String accept) throws Exception {
    long id = createStoredPost();

    var response = head(id, accept);
    assertEquals(406, response.statusCode());
    assertEquals("", response.body());
    assertEquals(0L, storedCount(id));
  }

  private HttpResponse<String> head(long id, String accept) throws Exception {
    var request = request(id).method("HEAD", HttpRequest.BodyPublishers.noBody());
    if (accept != null) {
      request.header("Accept", accept);
    }
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpRequest.Builder request(long id) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/posts/" + id))
        .timeout(Duration.ofSeconds(10));
  }

  private long createStoredPost() {
    commands.createPost("기존 제목", "기존 본문", Category.GENERAL, "작성자");
    return jdbc.queryForObject("SELECT id FROM posts", Long.class);
  }

  private long storedCount(long id) {
    return jdbc.queryForObject("SELECT view_count FROM posts WHERE id = ?", Long.class, id);
  }
}
