package org.sopt.post.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;

class FlywayMigrationTest {
  @Test
  void existingSchemaRequiresExplicitBaseline() throws Exception {
    String url = existingDatabase();
    assertThrows(FlywayException.class, () -> flyway(url).migrate());
    assertOriginalPost(url);
  }

  @Test
  void explicitBaselineSkipsInitialDdlAndUpgradesExistingPostWithZeroViews() throws Exception {
    String url = existingDatabase();
    Flyway flyway = flyway(url);
    flyway.baseline();
    assertEquals(1, flyway.migrate().migrationsExecuted);
    flyway.validate();
    assertOriginalPost(url);
    try (var connection = DriverManager.getConnection(url, "sa", "");
        var statement = connection.createStatement();
        var rows =
            statement.executeQuery(
                "SELECT \"version\", \"type\" FROM \"flyway_schema_history\" WHERE \"version\" IS NOT NULL ORDER BY \"installed_rank\"")) {
      assertTrue(rows.next());
      assertEquals("1", rows.getString(1));
      assertEquals("BASELINE", rows.getString(2));
      assertTrue(rows.next());
      assertEquals("2", rows.getString(1));
      assertEquals("SQL", rows.getString(2));
      assertFalse(rows.next());
    }
    try (var connection = DriverManager.getConnection(url, "sa", "");
        var statement = connection.createStatement();
        var rows = statement.executeQuery("SELECT view_count FROM posts")) {
      assertTrue(rows.next());
      assertEquals(0, rows.getLong(1));
    }
    assertEquals(0, flyway.migrate().migrationsExecuted);
  }

  private Flyway flyway(String url) {
    return Flyway.configure()
        .dataSource(url, "sa", "")
        .defaultSchema("public")
        .locations("classpath:db/migration")
        .baselineVersion("1")
        .baselineOnMigrate(false)
        .load();
  }

  private String existingDatabase() throws Exception {
    String url =
        "jdbc:h2:mem:"
            + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    try (var connection = DriverManager.getConnection(url, "sa", "");
        var statement = connection.createStatement()) {
      // 기존 PostgreSQL에서 확인한 구조. 마이그레이션 SQL과 독립적인 전환 전 fixture.
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
          VALUES ('기존 제목', '기존 본문', 'GENERAL', '기존 작성자', TIMESTAMP '2026-10-13 12:00:00')
          """);
    }
    return url;
  }

  private void assertOriginalPost(String url) throws Exception {
    try (var connection = DriverManager.getConnection(url, "sa", "");
        var statement = connection.createStatement();
        var rows = statement.executeQuery("SELECT title, content, author FROM posts")) {
      assertTrue(rows.next());
      assertEquals("기존 제목", rows.getString(1));
      assertEquals("기존 본문", rows.getString(2));
      assertEquals("기존 작성자", rows.getString(3));
      assertFalse(rows.next());
    }
  }
}
