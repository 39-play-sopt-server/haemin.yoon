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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

  @Autowired org.sopt.member.application.port.in.command.MemberCommandUseCase memberCommands;

  private long memberId(String name) {
    return memberCommands.createMember(name).getId();
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
    jdbc.update("DELETE FROM members");
    jdbc.execute("ALTER TABLE members ALTER COLUMN id RESTART WITH 2");
    jdbc.update(
        "INSERT INTO members (id, name, deleted, created_at, updated_at) OVERRIDING SYSTEM VALUE VALUES (1, '작성자', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
  }

  @ParameterizedTest
  @ValueSource(strings = {"title", "content"})
  void nulInCreateReturns400WithoutSaving(String field) throws Exception {
    var body = new java.util.LinkedHashMap<String, Object>();
    body.put("title", "제목");
    body.put("content", "본문");
    body.put("category", "GENERAL");
    body.put("memberId", 1L);
    body.put(field, "앞\0뒤");
    var response =
        mvc.perform(
                post("/posts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body)))
            .andReturn()
            .getResponse();
    assertEquals(400, response.getStatus());
    var envelope = json.readTree(response.getContentAsString());
    assertFalse(envelope.get("success").booleanValue());
    assertEquals("NUL_CHARACTER_NOT_ALLOWED", envelope.get("code").stringValue());
    assertTrue(envelope.get("data").isNull());
    assertEquals(0, envelope.get("violations").size());
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM posts", Integer.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"title", "content"})
  void nulInUpdateReturns400AndPreservesContentAndVersion(String field) throws Exception {
    commands.createPost("기존 제목", "기존 본문", Category.GENERAL, memberId("작성자"));
    long id = jdbc.queryForObject("SELECT id FROM posts", Long.class);
    var body = new java.util.LinkedHashMap<String, Object>();
    body.put("title", "변경 제목");
    body.put("content", "변경 본문");
    body.put("version", 0);
    body.put(field, "앞\0뒤");
    var response =
        mvc.perform(
                put("/posts/{id}", id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body)))
            .andReturn()
            .getResponse();
    assertEquals(400, response.getStatus());
    assertEquals(
        "NUL_CHARACTER_NOT_ALLOWED",
        json.readTree(response.getContentAsString()).get("code").stringValue());
    var stored = jdbc.queryForMap("SELECT title, content, version FROM posts WHERE id = ?", id);
    assertEquals("기존 제목", stored.get("title"));
    assertEquals("기존 본문", stored.get("content"));
    assertEquals(0L, stored.get("version"));
  }

  @Test
  void validTextIncludingLiteralNulEscapeIsPreservedInPostgreSql() throws Exception {
    String text = " 한글😀\n\t\\u0000 ";
    var body =
        java.util.Map.of("title", text, "content", text, "category", "GENERAL", "memberId", 1L);
    mvc.perform(
            post("/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
        .andExpect(status().isCreated());
    var stored = jdbc.queryForMap("SELECT title, content FROM posts");
    assertEquals(text, stored.get("title"));
    assertEquals(text, stored.get("content"));
  }

  @Test
  void emptyDatabaseIsMigratedAndValidatedByJpa() throws Exception {
    try (var connection = jdbc.getDataSource().getConnection()) {
      assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
      assertEquals(18, connection.getMetaData().getDatabaseMajorVersion());
    }
    assertEquals(
        List.of("1", "2", "3", "4", "5", "6", "7", "8", "9"),
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
          {"title":"post-%d","content":"본문","category":"GENERAL","memberId":1}
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
    commands.createPost("다른 게시글", "본문", Category.GENERAL, memberId("작성자"));
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
          .locations("classpath:db/migration", "classpath:db/postgresql")
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
              .locations("classpath:db/migration", "classpath:db/postgresql")
              .load();
      assertEquals(6, flyway.migrate().migrationsExecuted);
      var rows =
          jdbc.queryForList(
              "SELECT title, content, view_count, version, created_at, updated_at FROM "
                  + schema
                  + ".posts");
      assertEquals(1, rows.size());
      assertEquals("전환 전 제목", rows.getFirst().get("title"));
      assertEquals("전환 전 본문", rows.getFirst().get("content"));
      assertEquals(17L, rows.getFirst().get("view_count"));
      assertEquals(0L, rows.getFirst().get("version"));
      assertNotNull(rows.getFirst().get("updated_at"));
      assertEquals(rows.getFirst().get("created_at"), rows.getFirst().get("updated_at"));
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
              .locations("classpath:db/migration", "classpath:db/postgresql")
              .baselineVersion("1")
              .baselineOnMigrate(false)
              .load();
      assertThrows(FlywayException.class, flyway::migrate);
      flyway.baseline();
      assertEquals(8, flyway.migrate().migrationsExecuted);
      flyway.validate();
      try (var rows =
          statement.executeQuery(
              "SELECT title, content, view_count, version, created_at, updated_at FROM posts")) {
        assertTrue(rows.next());
        assertEquals("기존 제목", rows.getString("title"));
        assertEquals("기존 본문", rows.getString("content"));
        assertEquals(0, rows.getLong("view_count"));
        assertEquals(0, rows.getLong("version"));
        assertNotNull(rows.getTimestamp("updated_at"));
        assertEquals(rows.getTimestamp("created_at"), rows.getTimestamp("updated_at"));
        assertFalse(rows.next());
      }
      assertEquals(
          List.of(
              "BASELINE:1", "SQL:2", "SQL:3", "SQL:4", "SQL:5", "SQL:6", "SQL:7", "SQL:8", "SQL:9"),
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

  @Test
  void memberDeletionKeepsPostsAndNamesButRejectsNewPostsInPostgreSql() throws Exception {
    var response =
        mvc.perform(
                post("/members")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"홍길동\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse();
    long memberId = json.readTree(response.getContentAsString()).get("data").get("id").longValue();
    commands.createPost("회원 게시글", "본문", Category.GENERAL, memberId);
    long postId = jdbc.queryForObject("SELECT id FROM posts", Long.class);
    mvc.perform(delete("/members/{id}", memberId)).andExpect(status().isOk());
    assertTrue(
        jdbc.queryForObject("SELECT deleted FROM members WHERE id = ?", Boolean.class, memberId));
    mvc.perform(get("/posts"))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.data.items[0].author")
                .value("홍길동"));
    mvc.perform(get("/posts/{id}", postId))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.data.author")
                .value("홍길동"));
    mvc.perform(
            post("/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\"제목\",\"content\":\"본문\",\"category\":\"GENERAL\",\"memberId\":%d}"
                        .formatted(memberId)))
        .andExpect(status().isNotFound());
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM posts", Integer.class));
  }

  @Test
  void v6LinksEveryLegacyPostWithoutMergingNamesAndEnforcesMemberReference() {
    String schema = "v5_" + UUID.randomUUID().toString().replace("-", "");
    jdbc.execute("CREATE SCHEMA " + schema);
    try {
      Flyway.configure()
          .dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
          .defaultSchema(schema)
          .schemas(schema)
          .locations("classpath:db/migration", "classpath:db/postgresql")
          .target("5")
          .load()
          .migrate();
      for (int index = 0; index < 2; index++) {
        jdbc.update(
            "INSERT INTO "
                + schema
                + ".posts (title, content, category, author, created_at, updated_at, view_count, version) VALUES (?, '본문', 'GENERAL', ' 동명이인😀 ', TIMESTAMP '2026-10-13 12:00:00.123456', TIMESTAMP '2026-10-14 12:00:00.654321', 17, 3)",
            "기존 글" + index);
      }
      var flyway =
          Flyway.configure()
              .dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
              .defaultSchema(schema)
              .schemas(schema)
              .locations("classpath:db/migration", "classpath:db/postgresql")
              .target("6")
              .load();
      assertEquals(1, flyway.migrate().migrationsExecuted);
      var rows =
          jdbc.queryForList(
              "SELECT p.title, p.view_count, p.version, p.member_id, p.created_at, p.updated_at, m.name, m.deleted, m.created_at AS member_created_at, m.updated_at AS member_updated_at FROM "
                  + schema
                  + ".posts p JOIN "
                  + schema
                  + ".members m ON m.id = p.member_id ORDER BY p.id");
      assertEquals(2, rows.size());
      assertNotEquals(rows.get(0).get("member_id"), rows.get(1).get("member_id"));
      for (int index = 0; index < rows.size(); index++) {
        var row = rows.get(index);
        assertEquals("기존 글" + index, row.get("title"));
        assertEquals(" 동명이인😀 ", row.get("name"));
        assertEquals(false, row.get("deleted"));
        assertEquals(17L, row.get("view_count"));
        assertEquals(3L, row.get("version"));
        assertEquals(row.get("created_at"), row.get("member_created_at"));
        assertEquals(row.get("updated_at"), row.get("member_updated_at"));
      }
      assertThrows(
          org.springframework.dao.DataIntegrityViolationException.class,
          () -> jdbc.update("UPDATE " + schema + ".posts SET member_id = NULL"));
      assertThrows(
          org.springframework.dao.DataIntegrityViolationException.class,
          () -> jdbc.update("UPDATE " + schema + ".posts SET member_id = 999999"));
      assertEquals(0, flyway.migrate().migrationsExecuted);
    } finally {
      jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
    }
  }

  @Test
  void scrollsByCreationTimeWithStableTiesInPostgreSql() throws Exception {
    for (int index = 0; index < 5; index++) {
      commands.createPost(
          "page-" + index, "본문", index == 1 ? Category.QUESTION : Category.GENERAL, 1L);
    }
    jdbc.update("UPDATE posts SET created_at = TIMESTAMP '2026-01-02 00:00:00.123456'");
    jdbc.update(
        "UPDATE posts SET created_at = TIMESTAMP '2026-01-03 00:00:00.123456' WHERE title = 'page-0'");
    String cursor = null;
    for (int batch = 0; batch < 3; batch++) {
      var data = scroll(cursor, null);
      var items = data.get("items");
      var expected =
          List.of(List.of("page-0", "page-4"), List.of("page-3", "page-2"), List.of("page-1"))
              .get(batch);
      assertEquals(expected.size(), items.size());
      for (int item = 0; item < expected.size(); item++) {
        assertEquals(expected.get(item), items.get(item).get("title").stringValue());
        assertEquals("작성자", items.get(item).get("author").stringValue());
      }
      assertEquals(batch < 2, data.get("hasNext").booleanValue());
      if (batch < 2) cursor = data.get("nextCursor").stringValue();
      else assertTrue(data.get("nextCursor").isNull());
    }
    var filteredFirst = scroll(null, "GENERAL");
    var filteredLast = scroll(filteredFirst.get("nextCursor").stringValue(), "GENERAL");
    assertEquals(2, filteredLast.get("items").size());
    assertEquals("page-3", filteredLast.get("items").get(0).get("title").stringValue());
    assertEquals("page-2", filteredLast.get("items").get(1).get("title").stringValue());
    assertFalse(filteredLast.get("hasNext").booleanValue());
    assertTrue(filteredLast.get("nextCursor").isNull());
    assertEquals(0L, jdbc.queryForObject("SELECT SUM(view_count) FROM posts", Long.class));
  }

  @Test
  void scrollingSurvivesInsertionAndDeletionOfTheBoundaryInPostgreSql() throws Exception {
    for (int index = 0; index < 5; index++) {
      commands.createPost("scroll-" + index, "본문", Category.GENERAL, 1L);
    }
    jdbc.update("UPDATE posts SET created_at = TIMESTAMP '2026-01-02 00:00:00.123456'");
    var first = scroll(null, null);
    assertEquals("scroll-4", first.get("items").get(0).get("title").stringValue());
    assertEquals("scroll-3", first.get("items").get(1).get("title").stringValue());
    jdbc.update("DELETE FROM posts WHERE title IN ('scroll-4', 'scroll-3')");
    commands.createPost("새 글", "본문", Category.GENERAL, 1L);
    var second = scroll(first.get("nextCursor").stringValue(), null);
    assertEquals("scroll-2", second.get("items").get(0).get("title").stringValue());
    assertEquals("scroll-1", second.get("items").get(1).get("title").stringValue());
    var last = scroll(second.get("nextCursor").stringValue(), null);
    assertEquals(1, last.get("items").size());
    assertEquals("scroll-0", last.get("items").get(0).get("title").stringValue());
    assertFalse(last.get("hasNext").booleanValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {"스프링", "sPrInG", "%", "_", "!", "\\", "|", "'", "100%_!|"})
  void searchesLiteralTitlesAndContinuesFilteredScrollInPostgreSql(String keyword)
      throws Exception {
    String literal = keyword.toUpperCase(java.util.Locale.ROOT);
    commands.createPost("첫 " + literal, "본문", Category.GENERAL, 1L);
    commands.createPost("제목 검색 대상 아님", keyword, Category.GENERAL, 1L);
    commands.createPost("둘 " + literal, "본문", Category.GENERAL, 1L);
    commands.createPost("질문 " + literal, "본문", Category.QUESTION, 1L);
    commands.createPost("셋 " + literal, "본문", Category.GENERAL, 1L);
    jdbc.update("UPDATE posts SET created_at = TIMESTAMP '2026-01-02 00:00:00.123456'");
    var first = scroll(null, "GENERAL", keyword);
    assertEquals(2, first.get("items").size());
    assertEquals("셋 " + literal, first.get("items").get(0).get("title").stringValue());
    assertEquals("둘 " + literal, first.get("items").get(1).get("title").stringValue());
    assertTrue(first.get("hasNext").booleanValue());
    var last = scroll(first.get("nextCursor").stringValue(), "GENERAL", keyword);
    assertEquals(1, last.get("items").size());
    assertEquals("첫 " + literal, last.get("items").get(0).get("title").stringValue());
    assertFalse(last.get("hasNext").booleanValue());
    assertTrue(last.get("nextCursor").isNull());
    assertEquals(0L, jdbc.queryForObject("SELECT SUM(view_count) FROM posts", Long.class));
    mvc.perform(
            get("/posts")
                .param("cursor", first.get("nextCursor").stringValue())
                .param("category", "GENERAL")
                .param("keyword", "다른 검색어"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void combinesAuthorCategoryAndTitleWithCursorInPostgreSql() throws Exception {
    long another = memberId("작성자");
    for (int i = 0; i < 3; i++) {
      commands.createPost("Spring A" + i, "본문", Category.GENERAL, 1L);
      commands.createPost("Spring B" + i, "본문", Category.GENERAL, another);
      commands.createPost("Spring 질문" + i, "본문", Category.QUESTION, 1L);
    }
    jdbc.update("UPDATE posts SET created_at = TIMESTAMP '2026-01-01 00:00:00.123456'");
    String cursor = null;
    for (int i = 2; i >= 0; i--) {
      var request =
          get("/posts")
              .param("size", "1")
              .param("authorId", "1")
              .param("category", "GENERAL")
              .param("keyword", "spring");
      if (cursor != null) request.param("cursor", cursor);
      var response = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse();
      var data = json.readTree(response.getContentAsString()).get("data");
      assertEquals(1, data.get("items").size());
      assertEquals("Spring A" + i, data.get("items").get(0).get("title").stringValue());
      if (i > 0) cursor = data.get("nextCursor").stringValue();
      else assertFalse(data.get("hasNext").booleanValue());
    }
    memberCommands.deleteMember(1L);
    mvc.perform(get("/posts").param("authorId", "1")).andExpect(status().isOk());
  }

  private tools.jackson.databind.JsonNode scroll(String cursor, String category) throws Exception {
    return scroll(cursor, category, null);
  }

  private tools.jackson.databind.JsonNode scroll(String cursor, String category, String keyword)
      throws Exception {
    var request = get("/posts").param("size", "2");
    if (cursor != null) request.param("cursor", cursor);
    if (category != null) request.param("category", category);
    if (keyword != null) request.param("keyword", keyword);
    var response = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse();
    return json.readTree(response.getContentAsString()).get("data");
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
    commands.createPost("조회수 검증", "본문", Category.GENERAL, memberId("작성자"));
    return jdbc.queryForObject("SELECT id FROM posts", Long.class);
  }

  private long storedCount(long id) {
    return jdbc.queryForObject("SELECT view_count FROM posts WHERE id = ?", Long.class, id);
  }
}
