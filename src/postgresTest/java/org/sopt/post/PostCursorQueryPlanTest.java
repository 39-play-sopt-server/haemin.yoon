package org.sopt.post;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sopt.post.application.port.in.query.PostCursor;
import org.sopt.post.application.port.in.query.PostListQuery;
import org.sopt.post.application.port.in.query.PostQueryUseCase;
import org.sopt.post.domain.Category;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 실제 저장소가 생성한 SQL의 실행 계획으로 깊은 커서의 전체 재탐색을 방지합니다. */
@SpringBootTest(
    properties =
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=org.sopt.post.PostCursorQueryPlanTest$SqlCapture")
@Testcontainers
@Timeout(60)
class PostCursorQueryPlanTest {
  @Container static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:18.2");
  private static final LocalDateTime START = LocalDateTime.of(2026, 1, 1, 0, 0);

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", DATABASE::getJdbcUrl);
    properties.add("spring.datasource.username", DATABASE::getUsername);
    properties.add("spring.datasource.password", DATABASE::getPassword);
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired PostQueryUseCase queries;
  @Autowired JsonMapper json;

  @BeforeEach
  void prepareLargeListing() {
    jdbc.update("DELETE FROM posts");
    jdbc.update("DELETE FROM members");
    jdbc.update(
        "INSERT INTO members (id, name, deleted, created_at, updated_at) OVERRIDING SYSTEM VALUE VALUES (1, '작성자', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
    jdbc.update(
        """
        INSERT INTO posts (title, content, category, member_id, created_at, updated_at, view_count, version)
        SELECT 'post-' || g, repeat('x', 100),
          CASE g % 3 WHEN 0 THEN 'GENERAL' WHEN 1 THEN 'QUESTION' ELSE 'INFORMATION' END,
          1, TIMESTAMP '2026-01-01 00:00:00' + g * INTERVAL '1 second', CURRENT_TIMESTAMP, 0, 0
        FROM generate_series(1, 100000) g
        """);
    jdbc.execute("ANALYZE posts");
    jdbc.execute("ANALYZE members");
  }

  @ParameterizedTest
  @ValueSource(strings = {"ALL", "GENERAL"})
  void deepCursorSeeksIntoCompositeIndexInsteadOfFilteringEarlierRows(String filter) {
    Category category = filter.equals("ALL") ? null : Category.GENERAL;
    long boundaryId =
        jdbc.queryForObject("SELECT id FROM posts WHERE title = 'post-49998'", Long.class);
    var cursor = new PostCursor(START.plusSeconds(49998), boundaryId, category);
    var statements = new ArrayList<String>();
    SqlCapture.STATEMENTS.set(statements);
    try {
      var result = queries.getPosts(new PostListQuery(cursor, 20, category));
      assertEquals(20, result.posts().size());
      assertTrue(result.hasNext());
    } finally {
      SqlCapture.STATEMENTS.remove();
    }
    // Hibernate가 실제로 전송한 SQL을 그대로 EXPLAIN해 변환 과정의 회귀도 검증합니다.
    assertEquals(1, statements.size(), statements.toString());
    var parameters = new ArrayList<Object>();
    if (category != null) parameters.add(category.name());
    parameters.add(cursor.createdAt());
    parameters.add(cursor.id());
    parameters.add(21);
    String explain =
        jdbc.queryForObject(
            "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + statements.getFirst(),
            String.class,
            parameters.toArray());
    JsonNode plan = json.readTree(explain).get(0).get("Plan");
    JsonNode index = findCursorIndex(plan);
    assertNotNull(index, explain);
    assertEquals(21, index.get("Actual Rows").intValue(), explain);
    // 분포에 따라 카테고리 인덱스 대신 전체 최신순 인덱스를 선택할 수 있습니다.
    // 카테고리 필터는 허용하되 커서 경계를 읽은 뒤 제거하는 회귀는 거부합니다.
    if (index.has("Filter")) {
      String filterExpression = index.get("Filter").stringValue();
      assertFalse(filterExpression.contains("created_at"), explain);
      assertFalse(filterExpression.contains("id"), explain);
    }
  }

  private JsonNode findCursorIndex(JsonNode plan) {
    if (plan.has("Index Cond")) {
      String condition = plan.get("Index Cond").stringValue();
      if (condition.contains("ROW(")
          && condition.contains("created_at")
          && condition.contains("id")) {
        return plan;
      }
    }
    if (plan.has("Plans")) {
      for (JsonNode child : plan.get("Plans")) {
        JsonNode found = findCursorIndex(child);
        if (found != null) return found;
      }
    }
    return null;
  }

  /** 조회를 실행하는 현재 테스트 스레드의 SQL만 수집합니다. */
  public static class SqlCapture implements StatementInspector {
    static final ThreadLocal<List<String>> STATEMENTS = new ThreadLocal<>();

    @Override
    public String inspect(String sql) {
      var statements = STATEMENTS.get();
      if (statements != null) statements.add(sql);
      return sql;
    }
  }
}
