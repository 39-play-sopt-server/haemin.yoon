package org.sopt.post.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sopt.post.application.port.in.query.PostListQuery;
import org.sopt.post.application.port.out.LoadPostPort;
import org.sopt.post.application.port.out.SavePostPort;
import org.sopt.post.domain.Category;
import org.sopt.post.domain.Post;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PostDatabaseTest {
  @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entityManager;
  @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;
  @Autowired LoadPostPort reader;
  @Autowired SavePostPort writer;
  @Autowired JdbcTemplate jdbc;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
  @Autowired org.sopt.post.application.port.in.command.PostCommandUseCase commands;

  @Test
  void flywayCreatesSchemaBeforeJpaStarts() {
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT count(*) FROM \"flyway_schema_history\" WHERE \"version\" = '1' AND \"success\" = true",
            Integer.class));
    writer.save(new Post("마이그레이션 검증", "본문", Category.GENERAL, "작성자"));
    assertEquals(
        "마이그레이션 검증", reader.findAll(new PostListQuery(0, 100, null)).getFirst().getTitle());
  }

  @Test
  void managedPostUpdateIsCommittedWithoutExplicitSaveOrUpdateQuery() {
    writer.save(new Post("기존 제목", "기존 본문", Category.QUESTION, "작성자"));
    long id = reader.findAll(new PostListQuery(0, 100, null)).getFirst().getId();
    var transaction =
        new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    transaction.executeWithoutResult(
        status -> {
          var managed = reader.findById(id).orElseThrow();
          managed.update("변경 감지 제목", "변경 감지 본문");
        });
    assertEquals(
        "변경 감지 제목", jdbc.queryForObject("SELECT title FROM posts WHERE id = ?", String.class, id));
    assertEquals(
        "변경 감지 본문",
        jdbc.queryForObject("SELECT content FROM posts WHERE id = ?", String.class, id));
  }

  @Test
  void commandUpdateCommitsBothFieldsAndPreservesMetadata() {
    commands.createPost("기존 제목", "기존 본문", Category.INFORMATION, "작성자");
    var original = reader.findAll(new PostListQuery(0, 100, null)).getFirst();
    commands.updatePost(original.getId(), "수정 제목", "수정 본문", 0L);
    var updated = reader.findById(original.getId()).orElseThrow();
    assertEquals("수정 제목", updated.getTitle());
    assertEquals("수정 본문", updated.getContent());
    assertEquals(original.getCreatedAt(), updated.getCreatedAt());
    assertEquals(original.getCategory(), updated.getCategory());
    assertEquals(original.getAuthor(), updated.getAuthor());
  }

  @Test
  void failedDomainValidationLeavesDatabaseUnchanged() {
    commands.createPost("기존 제목", "기존 본문", Category.GENERAL, "작성자");
    long id = reader.findAll(new PostListQuery(0, 100, null)).getFirst().getId();
    assertThrows(
        org.sopt.post.domain.exception.PostException.class,
        () -> commands.updatePost(id, "변경되면 안 되는 제목", " ", 0L));
    var stored = reader.findById(id).orElseThrow();
    assertEquals("기존 제목", stored.getTitle());
    assertEquals("기존 본문", stored.getContent());
    assertEquals(0, stored.getVersion());
  }

  @Test
  void flushedEntityChangesRollBackWithTheBusinessTransaction() {
    commands.createPost("기존 제목", "기존 본문", Category.GENERAL, "작성자");
    long id = reader.findAll(new PostListQuery(0, 100, null)).getFirst().getId();
    var transaction =
        new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    assertThrows(
        IllegalStateException.class,
        () ->
            transaction.executeWithoutResult(
                status -> {
                  commands.updatePost(id, "롤백할 제목", "롤백할 본문", 0L);
                  entityManager.flush();
                  assertEquals(
                      1L,
                      jdbc.queryForObject(
                          "SELECT version FROM posts WHERE id = ?", Long.class, id));
                  assertEquals(
                      "롤백할 제목",
                      jdbc.queryForObject(
                          "SELECT title FROM posts WHERE id = ?", String.class, id));
                  throw new IllegalStateException("후속 처리 실패");
                }));
    var stored = reader.findById(id).orElseThrow();
    assertEquals("기존 제목", stored.getTitle());
    assertEquals("기존 본문", stored.getContent());
    assertEquals(0, stored.getVersion());
  }

  @BeforeEach
  void clearPosts() {
    jdbc.update("DELETE FROM posts");
  }

  @Test
  void postItselfIsManagedAndReceivesGeneratedId() {
    assertTrue(
        entityManagerFactory.getMetamodel().getEntities().stream()
            .anyMatch(entity -> entity.getJavaType() == Post.class));
    Post post = new Post("엔티티 저장", "본문", Category.GENERAL, "작성자");
    writer.save(post);
    assertNotNull(post.getId());
    assertEquals("엔티티 저장", reader.findById(post.getId()).orElseThrow().getTitle());
  }

  @Test
  void databaseGeneratesIdDuringInsert() {
    Post pending = new Post("자동 ID", "본문", Category.GENERAL, "작성자");
    assertNull(pending.getId());
    writer.save(pending);
    Post saved = reader.findAll(new PostListQuery(0, 100, null)).getFirst();
    assertTrue(saved.getId() > 0, "ID는 저장 시 DB에서 생성되어야 합니다.");
    assertEquals(
        "YES",
        jdbc.queryForObject(
            """
        SELECT is_identity FROM information_schema.columns
        WHERE lower(table_name) = 'posts' AND lower(column_name) = 'id'
        """,
            String.class));
  }

  @Test
  void savesIntoDatabaseAndReloadsEveryField() {
    Post pending = new Post("제목", "본문", Category.QUESTION, "작성자");
    writer.save(pending);
    Post loaded = reader.findAll(new PostListQuery(0, 100, null)).getFirst();
    assertEquals(
        "본문",
        jdbc.queryForObject(
            "SELECT content FROM posts WHERE id = ?", String.class, loaded.getId()));
    assertEquals("제목", loaded.getTitle());
    assertEquals(Category.QUESTION, loaded.getCategory());
    assertEquals("작성자", loaded.getAuthor());
    assertEquals(
        pending.getCreatedAt().withNano(pending.getCreatedAt().getNano() / 1000 * 1000),
        loaded.getCreatedAt());
  }

  @Test
  void updatesOnlyTitleAndContentAndDeletesExistingRows() {
    writer.save(new Post("제목", "본문", Category.INFORMATION, "작성자"));
    Post post = reader.findAll(new PostListQuery(0, 100, null)).getFirst();
    LocalDateTime createdAt = post.getCreatedAt();
    commands.updatePost(post.getId(), "수정 제목", "수정 본문", 0L);
    Post updated = reader.findById(post.getId()).orElseThrow();
    assertEquals("수정 제목", updated.getTitle());
    assertEquals("수정 본문", updated.getContent());
    assertEquals(Category.INFORMATION, updated.getCategory());
    assertEquals("작성자", updated.getAuthor());
    assertEquals(createdAt, updated.getCreatedAt());
    commands.deletePost(post.getId());
    assertThrows(
        org.sopt.post.domain.exception.PostException.class,
        () -> commands.deletePost(post.getId()));
    assertThrows(
        org.sopt.post.domain.exception.PostException.class,
        () -> commands.updatePost(post.getId(), "없는 글", "본문", 0L));
    assertTrue(reader.findById(post.getId()).isEmpty());
  }

  @Test
  void saveWithExistingIdDoesNotOverwriteExistingPost() {
    writer.save(new Post("원본", "원본 본문", Category.GENERAL, "작성자"));
    long id = reader.findAll(new PostListQuery(0, 100, null)).getFirst().getId();
    var duplicate = new Post("중복", "중복 본문", Category.QUESTION, "다른 작성자");
    org.springframework.test.util.ReflectionTestUtils.setField(duplicate, "id", id);
    assertThrows(IllegalArgumentException.class, () -> writer.save(duplicate));
    assertEquals("원본", reader.findById(id).orElseThrow().getTitle());
  }

  @Test
  void listsInIdOrderAndDoesNotReuseDeletedIds() {
    writer.save(new Post("첫째", "본문", Category.GENERAL, "작성자"));
    long first = reader.findAll(new PostListQuery(0, 100, null)).getFirst().getId();
    writer.save(new Post("둘째", "본문", Category.GENERAL, "작성자"));
    long second = reader.findAll(new PostListQuery(0, 100, null)).getLast().getId();
    assertTrue(second > first);
    assertEquals(
        java.util.List.of("첫째", "둘째"),
        reader.findAll(new PostListQuery(0, 100, null)).stream().map(Post::getTitle).toList());
    commands.deletePost(second);
    writer.save(new Post("셋째", "본문", Category.GENERAL, "작성자"));
    assertTrue(reader.findAll(new PostListQuery(0, 100, null)).getLast().getId() > second);
  }
}
