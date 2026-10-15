package org.sopt.post;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sopt.post.adapter.out.persistence.PostPersistenceAdapter;
import org.sopt.post.application.facade.PostFacade;
import org.sopt.post.application.port.in.command.PostCommandUseCase;
import org.sopt.post.domain.Category;
import org.sopt.post.domain.Post;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Timeout(60)
class PostgreSqlPostIntegrationTest {
  @Container
  static final PostgreSQLContainer DATABASE =
      new PostgreSQLContainer("postgres:18.2")
          .withCommand("postgres", "-c", "lock_timeout=10s", "-c", "statement_timeout=20s");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", DATABASE::getJdbcUrl);
    properties.add("spring.datasource.username", DATABASE::getUsername);
    properties.add("spring.datasource.password", DATABASE::getPassword);
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired JsonMapper json;
  @Autowired PostCommandUseCase commands;
  @Autowired PostFacade facade;
  @Autowired PlatformTransactionManager transactionManager;
  @PersistenceContext EntityManager entityManager;
  @MockitoSpyBean PostPersistenceAdapter persistence;

  @BeforeEach
  void clearPosts() {
    jdbc.update("DELETE FROM posts");
  }

  @Test
  void emptyDatabaseIsMigratedAndValidatedByJpa() throws Exception {
    try (var connection = jdbc.getDataSource().getConnection()) {
      assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
      assertEquals(18, connection.getMetaData().getDatabaseMajorVersion());
    }
    assertEquals(
        List.of("1", "2", "3", "4"),
        jdbc.queryForList(
            "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank",
            String.class));
    long id = createPost();
    assertEquals(0, storedCount(id));
  }

  @Test
  void concurrentHttpCreatesPersistEveryPostWithUniqueIds() throws Exception {
    concurrently(
        40,
        index -> {
          mvc.perform(
                  post("/posts")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
          {"title":"post-%d","content":"본문","category":"GENERAL","author":"작성자"}
          """
                              .formatted(index)))
              .andExpect(status().isCreated());
          return null;
        });
    assertEquals(40, jdbc.queryForObject("SELECT COUNT(*) FROM posts", Integer.class));
    assertEquals(40, jdbc.queryForObject("SELECT COUNT(DISTINCT id) FROM posts", Integer.class));
    assertEquals(
        IntStream.range(0, 40).mapToObj(i -> "post-" + i).sorted().toList(),
        jdbc.queryForList("SELECT title FROM posts ORDER BY title", String.class));
    assertEquals(0L, jdbc.queryForObject("SELECT SUM(view_count) FROM posts", Long.class));
  }

  @Test
  void concurrentHttpDetailsReturnEveryIncrementWithoutLoss() throws Exception {
    long id = createPost();
    List<Long> counts =
        concurrently(
            40,
            index -> {
              String response =
                  mvc.perform(get("/posts/{id}", id))
                      .andExpect(status().isOk())
                      .andReturn()
                      .getResponse()
                      .getContentAsString();
              return json.readTree(response).get("data").get("viewCount").longValue();
            });
    assertEquals(40, storedCount(id));
    assertEquals(LongStream.rangeClosed(1, 40).boxed().toList(), counts.stream().sorted().toList());
  }

  @Test
  void staleContentUpdateDoesNotOverwriteConcurrentViewIncrement() {
    long id = createPost();
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              Post stale = entityManager.find(Post.class, id);
              assertEquals(0, stale.getViewCount());
              try (var executor = Executors.newSingleThreadExecutor()) {
                assertEquals(
                    1,
                    executor
                        .submit(() -> facade.getPostDetail(id).getViewCount())
                        .get(15, TimeUnit.SECONDS));
              } catch (Exception exception) {
                throw new AssertionError(exception);
              }
              stale.update("동시 수정 제목", "동시 수정 본문");
            });
    assertEquals(1, storedCount(id));
    assertEquals(
        "동시 수정 제목", jdbc.queryForObject("SELECT title FROM posts WHERE id = ?", String.class, id));
    assertEquals(
        "동시 수정 본문",
        jdbc.queryForObject("SELECT content FROM posts WHERE id = ?", String.class, id));
  }

  @Test
  void lockingOnePostDoesNotBlockDetailOfAnotherPost() {
    long id = createPost();
    commands.createPost("다른 게시글", "본문", Category.GENERAL, "작성자");
    long otherId = jdbc.queryForObject("SELECT MAX(id) FROM posts", Long.class);
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              facade.getPostDetail(id);
              try (var executor = Executors.newSingleThreadExecutor()) {
                assertEquals(
                    1,
                    executor
                        .submit(() -> facade.getPostDetail(otherId).getViewCount())
                        .get(15, TimeUnit.SECONDS));
              } catch (Exception exception) {
                throw new AssertionError(exception);
              }
            });
    assertEquals(1, storedCount(id));
    assertEquals(1, storedCount(otherId));
  }

  @Test
  void viewIncrementRollsBackWithItsTransaction() {
    long id = createPost();
    assertThrows(
        IllegalStateException.class,
        () ->
            new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                    status -> {
                      facade.getPostDetail(id);
                      assertEquals(1, storedCount(id));
                      throw new IllegalStateException("후속 처리 실패");
                    }));
    assertEquals(0, storedCount(id));
  }

  @Test
  void concurrentTransactionsCannotBothCommitEditsFromTheSameSnapshot() throws Exception {
    long id = createPost();
    var loaded = new CyclicBarrier(2);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var results = new ArrayList<Future<Boolean>>();
      for (int i = 0; i < 2; i++) {
        int editor = i;
        results.add(
            executor.submit(
                () -> {
                  try {
                    new TransactionTemplate(transactionManager)
                        .executeWithoutResult(
                            status -> {
                              Post post = entityManager.find(Post.class, id);
                              try {
                                loaded.await(10, TimeUnit.SECONDS);
                              } catch (Exception exception) {
                                throw new AssertionError(exception);
                              }
                              post.update("editor-" + editor, "body-" + editor);
                            });
                    return true;
                  } catch (OptimisticLockingFailureException conflict) {
                    return false;
                  }
                }));
      }
      int committed = 0;
      for (var result : results) {
        if (result.get(30, TimeUnit.SECONDS)) committed++;
      }
      assertEquals(1, committed);
    }
    assertEquals(1L, jdbc.queryForObject("SELECT version FROM posts WHERE id = ?", Long.class, id));
  }

  @Test
  void concurrentHttpEditsRejectAllButOneRequestFromSameVersion() throws Exception {
    long id = createPost();
    var responses =
        concurrently(
            8,
            index ->
                mvc.perform(
                        put("/posts/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                """
        {"title":"editor-%d","content":"body-%d","version":0}
        """
                                    .formatted(index, index)))
                    .andReturn()
                    .getResponse());
    assertEquals(1, responses.stream().filter(response -> response.getStatus() == 200).count());
    assertEquals(7, responses.stream().filter(response -> response.getStatus() == 409).count());
    for (var response : responses) {
      if (response.getStatus() == 409) {
        assertEquals(
            "CONFLICT", json.readTree(response.getContentAsString()).get("code").stringValue());
      }
    }
    int winner =
        IntStream.range(0, responses.size())
            .filter(i -> responses.get(i).getStatus() == 200)
            .findFirst()
            .orElseThrow();
    assertEquals(
        "editor-" + winner,
        jdbc.queryForObject("SELECT title FROM posts WHERE id = ?", String.class, id));
    assertEquals(
        "body-" + winner,
        jdbc.queryForObject("SELECT content FROM posts WHERE id = ?", String.class, id));
    assertEquals(1L, jdbc.queryForObject("SELECT version FROM posts WHERE id = ?", Long.class, id));
  }

  @Test
  void commitTimeConflictIsReturnedAs409OverHttp() throws Exception {
    long id = createPost();
    var loaded = new CyclicBarrier(2);
    // 실제 조회를 수행한 후 두 HTTP 요청의 스냅샷을 일치시킵니다. 저장·커밋은 대체하지 않습니다.
    doAnswer(
            invocation -> {
              var result = invocation.callRealMethod();
              loaded.await(10, TimeUnit.SECONDS);
              return result;
            })
        .when(persistence)
        .findById(id);
    var responses =
        concurrently(
            2,
            index ->
                mvc.perform(
                        put("/posts/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                """
        {"title":"editor-%d","content":"body-%d","version":0}
        """
                                    .formatted(index, index)))
                    .andReturn()
                    .getResponse());
    assertEquals(1, responses.stream().filter(response -> response.getStatus() == 200).count());
    var conflict =
        responses.stream()
            .filter(response -> response.getStatus() == 409)
            .findFirst()
            .orElseThrow();
    assertEquals(
        "CONFLICT", json.readTree(conflict.getContentAsString()).get("code").stringValue());
    assertEquals(1L, jdbc.queryForObject("SELECT version FROM posts WHERE id = ?", Long.class, id));
  }

  @Test
  void addingVersionToExistingV3DataPreservesContentAndViews() throws Exception {
    String schema = "v3_" + UUID.randomUUID().toString().replace("-", "");
    jdbc.execute("CREATE SCHEMA " + schema);
    try {
      Flyway.configure()
          .dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
          .defaultSchema(schema)
          .schemas(schema)
          .locations("classpath:db/migration")
          .target("3")
          .load()
          .migrate();
      jdbc.update(
          "INSERT INTO "
              + schema
              + ".posts (title, content, category, author, created_at, view_count) VALUES (?, ?, 'GENERAL', ?, TIMESTAMP '2026-10-13 12:00:00', ?)",
          "전환 전 제목",
          "전환 전 본문",
          "작성자",
          17);
      var flyway =
          Flyway.configure()
              .dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
              .defaultSchema(schema)
              .schemas(schema)
              .locations("classpath:db/migration")
              .load();
      assertEquals(1, flyway.migrate().migrationsExecuted);
      var rows =
          jdbc.queryForList("SELECT title, content, view_count, version FROM " + schema + ".posts");
      assertEquals(1, rows.size());
      assertEquals("전환 전 제목", rows.getFirst().get("title"));
      assertEquals("전환 전 본문", rows.getFirst().get("content"));
      assertEquals(17L, rows.getFirst().get("view_count"));
      assertEquals(0L, rows.getFirst().get("version"));
      assertEquals(0, flyway.migrate().migrationsExecuted);
    } finally {
      jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
    }
  }

  @Test
  void explicitBaselinePreservesExistingPostAndAppliesRemainingMigrations() throws Exception {
    String schema = "legacy_" + UUID.randomUUID().toString().replace("-", "");
    jdbc.execute("CREATE SCHEMA " + schema);
    try (var connection =
            DriverManager.getConnection(
                DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        var statement = connection.createStatement()) {
      connection.setSchema(schema);
      // 전환 전 스키마 fixture는 현재 마이그레이션 파일과 독립적으로 정의합니다.
      statement.execute(
          """
          CREATE TABLE posts (
            id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
            title TEXT NOT NULL, content TEXT NOT NULL,
            category VARCHAR(30) NOT NULL, author TEXT NOT NULL,
            created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL
          )
          """);
      statement.execute(
          """
          INSERT INTO posts (title, content, category, author, created_at)
          VALUES ('기존 제목', '기존 본문', 'GENERAL', '작성자', TIMESTAMP '2026-10-13 12:00:00')
          """);
      var flyway =
          Flyway.configure()
              .dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
              .defaultSchema(schema)
              .schemas(schema)
              .locations("classpath:db/migration")
              .baselineVersion("1")
              .baselineOnMigrate(false)
              .load();
      assertThrows(FlywayException.class, flyway::migrate);
      flyway.baseline();
      assertEquals(3, flyway.migrate().migrationsExecuted);
      flyway.validate();
      try (var rows =
          statement.executeQuery("SELECT title, content, view_count, version FROM posts")) {
        assertTrue(rows.next());
        assertEquals("기존 제목", rows.getString("title"));
        assertEquals("기존 본문", rows.getString("content"));
        assertEquals(0, rows.getLong("view_count"));
        assertEquals(0, rows.getLong("version"));
        assertFalse(rows.next());
      }
      assertEquals(
          List.of("BASELINE:1", "SQL:2", "SQL:3", "SQL:4"),
          jdbc.queryForList(
              "SELECT type || ':' || version FROM "
                  + schema
                  + ".flyway_schema_history WHERE success = true ORDER BY installed_rank",
              String.class));
      assertEquals(0, flyway.migrate().migrationsExecuted);
    } finally {
      jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
    }
  }

  private <T> List<T> concurrently(int count, ConcurrentAction<T> action) throws Exception {
    int workers = Math.min(8, count);
    var barrier = new CyclicBarrier(workers);
    try (var executor = Executors.newFixedThreadPool(workers)) {
      var futures = new ArrayList<Future<T>>();
      for (int i = 0; i < count; i++) {
        int index = i;
        futures.add(
            executor.submit(
                () -> {
                  barrier.await(10, TimeUnit.SECONDS);
                  return action.run(index);
                }));
      }
      var results = new ArrayList<T>();
      for (var future : futures) {
        results.add(future.get(30, TimeUnit.SECONDS));
      }
      return results;
    }
  }

  @FunctionalInterface
  private interface ConcurrentAction<T> {
    T run(int index) throws Exception;
  }

  private long createPost() {
    commands.createPost("조회수 검증", "본문", Category.GENERAL, "작성자");
    return jdbc.queryForObject("SELECT id FROM posts", Long.class);
  }

  private long storedCount(long id) {
    return jdbc.queryForObject("SELECT view_count FROM posts WHERE id = ?", Long.class, id);
  }
}
