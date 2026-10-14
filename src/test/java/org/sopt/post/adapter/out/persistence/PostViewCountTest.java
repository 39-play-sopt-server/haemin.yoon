package org.sopt.post.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sopt.post.application.facade.PostFacade;
import org.sopt.post.application.port.in.command.PostCommandUseCase;
import org.sopt.post.application.port.in.query.PostQueryUseCase;
import org.sopt.post.domain.Category;
import org.sopt.post.domain.Post;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    properties =
        "spring.datasource.url=jdbc:h2:mem:post-views;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostViewCountTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired PostCommandUseCase commands;
  @Autowired PostQueryUseCase queries;
  @Autowired PostFacade facade;
  @Autowired PlatformTransactionManager transactionManager;
  @PersistenceContext EntityManager entityManager;

  @BeforeEach
  void clearPosts() {
    jdbc.update("DELETE FROM posts");
  }

  @Test
  void newPostStartsAtZeroAndListingDoesNotIncrement() throws Exception {
    createPost();
    for (int i = 0; i < 2; i++) {
      mvc.perform(get("/posts"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data[0].viewCount").value(0));
    }
  }

  @Test
  void eachSuccessfulDetailReturnsAndPersistsIncrementedCount() throws Exception {
    long id = createPost();
    for (int count = 1; count <= 2; count++) {
      mvc.perform(get("/posts/{id}", id))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.viewCount").value(count));
      assertEquals(count, storedCount(id));
    }
  }

  @Test
  void missingPostDoesNotIncrementOtherPosts() throws Exception {
    long id = createPost();
    mvc.perform(get("/posts/{id}", Long.MAX_VALUE))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    assertEquals(0, storedCount(id));
  }

  @Test
  void directQueryDoesNotIncrementViews() {
    long id = createPost();
    assertEquals(0, queries.getPost(id).getViewCount());
    assertEquals(0, queries.getPost(id).getViewCount());
    assertEquals(0, storedCount(id));
  }

  @Test
  void updatingContentDoesNotResetViews() {
    long id = createPost();
    facade.getPostDetail(id);
    commands.updatePost(id, "수정 제목", "수정 본문");
    assertEquals(1, storedCount(id));
  }

  @Test
  void staleContentUpdateCannotOverwriteConcurrentViewIncrement() {
    long id = createPost();
    var transaction = new TransactionTemplate(transactionManager);
    transaction.executeWithoutResult(
        status -> {
          Post stale = entityManager.find(Post.class, id);
          assertEquals(0, stale.getViewCount());
          try (var executor = Executors.newSingleThreadExecutor()) {
            executor.submit(() -> facade.getPostDetail(id)).get(10, TimeUnit.SECONDS);
          } catch (Exception exception) {
            throw new AssertionError(exception);
          }
          stale.update("동시 수정 제목", "동시 수정 본문");
        });
    assertEquals(1, storedCount(id));
    assertEquals(
        "동시 수정 제목", jdbc.queryForObject("SELECT title FROM posts WHERE id = ?", String.class, id));
  }

  @Test
  void detailIncrementRollsBackWithTransaction() {
    long id = createPost();
    var transaction = new TransactionTemplate(transactionManager);
    assertThrows(
        IllegalStateException.class,
        () ->
            transaction.executeWithoutResult(
                status -> {
                  facade.getPostDetail(id);
                  assertEquals(1, storedCount(id));
                  throw new IllegalStateException("후속 처리 실패");
                }));
    assertEquals(0, storedCount(id));
  }

  @Test
  void concurrentDetailsDoNotLoseIncrements() throws Exception {
    long id = createPost();
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(8)) {
      var futures = new ArrayList<Future<?>>();
      for (int i = 0; i < 40; i++) {
        futures.add(
            executor.submit(
                () -> {
                  assertTrue(start.await(10, TimeUnit.SECONDS));
                  facade.getPostDetail(id);
                  return null;
                }));
      }
      start.countDown();
      for (var future : futures) {
        future.get(20, TimeUnit.SECONDS);
      }
    }
    assertEquals(40, storedCount(id));
  }

  private long createPost() {
    commands.createPost("조회수 검증", "본문", Category.GENERAL, "작성자");
    return jdbc.queryForObject("SELECT id FROM posts", Long.class);
  }

  private long storedCount(long id) {
    return jdbc.queryForObject("SELECT view_count FROM posts WHERE id = ?", Long.class, id);
  }
}
