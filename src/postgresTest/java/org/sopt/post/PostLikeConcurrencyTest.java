package org.sopt.post;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sopt.member.application.port.in.command.MemberCommandUseCase;
import org.sopt.post.adapter.out.persistence.PostLikePersistenceAdapter;
import org.sopt.post.application.facade.PostLikeFacade;
import org.sopt.post.application.port.in.command.PostCommandUseCase;
import org.sopt.post.domain.Category;
import org.sopt.post.domain.Post;
import org.sopt.post.domain.exception.PostErrorCode;
import org.sopt.post.domain.exception.PostException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@Timeout(40)
class PostLikeConcurrencyTest {
  @Container static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:18.2");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", DATABASE::getJdbcUrl);
    properties.add("spring.datasource.username", DATABASE::getUsername);
    properties.add("spring.datasource.password", DATABASE::getPassword);
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired PostCommandUseCase posts;
  @Autowired MemberCommandUseCase members;
  @Autowired PostLikeFacade likes;
  @MockitoSpyBean PostLikePersistenceAdapter storage;
  long postId;

  @BeforeEach
  void prepare() {
    jdbc.update("DELETE FROM posts");
    jdbc.update("DELETE FROM members");
    long author = members.createMember("작성자").id();
    posts.createPost("좋아요", "본문", Category.GENERAL, author);
    postId = jdbc.queryForObject("SELECT id FROM posts", Long.class);
  }

  @Test
  void concurrentDistinctMembersDoNotLoseIncrementsOrDecrements() throws Exception {
    var memberIds = new ArrayList<Long>();
    for (int i = 0; i < 32; i++) memberIds.add(members.createMember("회원" + i).id());
    concurrently(32, index -> likes.like(postId, memberIds.get(index)));
    assertCount(32);
    concurrently(32, index -> likes.unlike(postId, memberIds.get(index)));
    assertCount(0);
  }

  @Test
  void concurrentDuplicateRequestsRemainIdempotent() throws Exception {
    long memberId = members.createMember("좋아요 회원").id();
    concurrently(32, index -> likes.like(postId, memberId));
    assertCount(1);
    concurrently(32, index -> likes.unlike(postId, memberId));
    assertCount(0);
  }

  @Test
  void racingLikeUnlikeAndViewCountRemainConsistent() throws Exception {
    long memberId = members.createMember("좋아요 회원").id();
    concurrently(
        48,
        index -> {
          if (index % 3 == 0) likes.like(postId, memberId);
          else if (index % 3 == 1) likes.unlike(postId, memberId);
          else posts.increaseViewCount(postId);
          return null;
        });
    long rows =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM post_likes WHERE post_id = ?", Long.class, postId);
    assertTrue(rows == 0 || rows == 1);
    assertCount(rows);
    assertEquals(
        16, jdbc.queryForObject("SELECT view_count FROM posts WHERE id = ?", Long.class, postId));
    assertEquals(
        0, jdbc.queryForObject("SELECT version FROM posts WHERE id = ?", Long.class, postId));
  }

  @Test
  void failureAfterCounterUpdateRollsBackRelationshipAndCounterBeforeRetry() {
    long memberId = members.createMember("좋아요 회원").id();
    var transactionIds = new ArrayList<Long>();
    doAnswer(
            invocation -> {
              transactionIds.add(jdbc.queryForObject("SELECT txid_current()", Long.class));
              Object result = invocation.callRealMethod();
              if (transactionIds.size() == 1) throw new CannotAcquireLockException("재시도 롤백 검증");
              return result;
            })
        .when(storage)
        .increaseCount(any(Post.class));
    assertEquals(1, likes.like(postId, memberId).likeCount());
    assertEquals(2, transactionIds.size());
    assertNotEquals(transactionIds.get(0), transactionIds.get(1));
    assertCount(1);
  }

  @Test
  void nonRetryableFailureAfterRelationshipInsertAlsoRollsBack() {
    long memberId = members.createMember("좋아요 회원").id();
    doAnswer(
            invocation -> {
              throw new DataIntegrityViolationException("카운터 갱신 실패");
            })
        .when(storage)
        .increaseCount(any(Post.class));
    assertThrows(DataIntegrityViolationException.class, () -> likes.like(postId, memberId));
    assertCount(0);
  }

  @Test
  void realPostgreSqlDeadlockRollsBackAndRetriesInANewTransaction() throws Exception {
    long memberId = members.createMember("좋아요 회원").id();
    likes.like(postId, memberId);
    var childLocked = new CountDownLatch(1);
    var parentLocked = new CountDownLatch(1);
    var attempts = new AtomicInteger();
    var deadlocks = new AtomicInteger();
    var transactionIds = new ArrayList<Long>();
    doAnswer(
            invocation -> {
              transactionIds.add(jdbc.queryForObject("SELECT txid_current()", Long.class));
              if (attempts.incrementAndGet() == 1) {
                jdbc.execute("SET LOCAL deadlock_timeout = '100ms'");
                parentLocked.countDown();
              }
              try {
                return invocation.callRealMethod();
              } catch (PessimisticLockingFailureException failure) {
                deadlocks.incrementAndGet();
                throw failure;
              }
            })
        .when(storage)
        .delete(anyLong(), anyLong());

    try (var executor = Executors.newSingleThreadExecutor()) {
      // 역순으로 접근하는 외부 작업을 주입하여 실제 DB 데드락을 발생시킵니다.
      var adversary =
          executor.submit(
              () -> {
                try (var connection = jdbc.getDataSource().getConnection();
                    var statement = connection.createStatement()) {
                  connection.setAutoCommit(false);
                  statement.execute("SET LOCAL deadlock_timeout = '5s'");
                  statement.execute("SET LOCAL statement_timeout = '10s'");
                  statement
                      .executeQuery(
                          "SELECT id FROM post_likes WHERE post_id = " + postId + " FOR UPDATE")
                      .close();
                  childLocked.countDown();
                  assertTrue(parentLocked.await(10, TimeUnit.SECONDS));
                  statement.executeUpdate(
                      "UPDATE posts SET like_count = like_count WHERE id = " + postId);
                  connection.commit();
                }
                return null;
              });
      assertTrue(childLocked.await(10, TimeUnit.SECONDS));
      var result = likes.unlike(postId, memberId);
      assertFalse(result.liked());
      assertEquals(0, result.likeCount());
      adversary.get(15, TimeUnit.SECONDS);
    }
    assertEquals(1, deadlocks.get());
    assertEquals(2, attempts.get());
    assertNotEquals(transactionIds.get(0), transactionIds.get(1));
    assertCount(0);
  }

  @Test
  void lockQueryTimeoutIsRetriedAndReturnsServiceUnavailableAfterThreeAttempts() throws Exception {
    long memberId = members.createMember("좋아요 회원").id();
    try (var connection = jdbc.getDataSource().getConnection();
        var statement = connection.createStatement()) {
      connection.setAutoCommit(false);
      statement.executeQuery("SELECT id FROM posts WHERE id = " + postId + " FOR UPDATE").close();
      try (var executor = Executors.newSingleThreadExecutor()) {
        var request =
            executor.submit(
                () -> assertThrows(PostException.class, () -> likes.like(postId, memberId)));
        assertEquals(
            PostErrorCode.POST_LIKE_BUSY, request.get(15, TimeUnit.SECONDS).getErrorCode());
      } finally {
        connection.rollback();
      }
    }
    assertCount(0);
  }

  @Test
  void relationshipLockTimeoutAfterParentLockRollsBackAndExhaustsRetries() throws Exception {
    long memberId = members.createMember("좋아요 회원").id();
    likes.like(postId, memberId);
    var parentLocked = new CountDownLatch(1);
    var transactionIds = new ArrayList<Long>();
    doAnswer(
            invocation -> {
              transactionIds.add(jdbc.queryForObject("SELECT txid_current()", Long.class));
              parentLocked.countDown();
              return invocation.callRealMethod();
            })
        .when(storage)
        .delete(anyLong(), anyLong());

    try (var connection = jdbc.getDataSource().getConnection();
        var statement = connection.createStatement();
        var executor = Executors.newSingleThreadExecutor()) {
      connection.setAutoCommit(false);
      statement
          .executeQuery("SELECT id FROM post_likes WHERE post_id = " + postId + " FOR UPDATE")
          .close();
      var request =
          executor.submit(
              () -> assertThrows(PostException.class, () -> likes.unlike(postId, memberId)));
      try {
        assertTrue(parentLocked.await(5, TimeUnit.SECONDS));
        assertEquals(
            PostErrorCode.POST_LIKE_BUSY, request.get(12, TimeUnit.SECONDS).getErrorCode());
        assertEquals(3, transactionIds.size());
        assertEquals(3, transactionIds.stream().distinct().count());
        assertCount(1);
        // 좋아요 행의 차단은 유지하면서도 실패한 요청의 부모 잠금은 모두 해제되어야 합니다.
        statement
            .executeQuery("SELECT id FROM posts WHERE id = " + postId + " FOR UPDATE NOWAIT")
            .close();
      } finally {
        // 수정 전 구현에서도 Future/Executor 정리 중 무한 대기하지 않도록 먼저 차단을 해제합니다.
        connection.rollback();
      }
    }
    assertFalse(likes.unlike(postId, memberId).liked());
    assertCount(0);
  }

  @Test
  void relationshipLockTimeoutRetriesSuccessfullyAfterBlockerReleases() throws Exception {
    long memberId = members.createMember("좋아요 회원").id();
    likes.like(postId, memberId);
    var timeoutObserved = new CountDownLatch(1);
    var transactionIds = new ArrayList<Long>();
    doAnswer(
            invocation -> {
              transactionIds.add(jdbc.queryForObject("SELECT txid_current()", Long.class));
              try {
                return invocation.callRealMethod();
              } catch (PessimisticLockingFailureException failure) {
                assertEquals(
                    "55P03",
                    assertInstanceOf(SQLException.class, failure.getMostSpecificCause())
                        .getSQLState());
                timeoutObserved.countDown();
                throw failure;
              }
            })
        .when(storage)
        .delete(anyLong(), anyLong());

    try (var connection = jdbc.getDataSource().getConnection();
        var statement = connection.createStatement();
        var executor = Executors.newSingleThreadExecutor()) {
      connection.setAutoCommit(false);
      statement
          .executeQuery("SELECT id FROM post_likes WHERE post_id = " + postId + " FOR UPDATE")
          .close();
      var request = executor.submit(() -> likes.unlike(postId, memberId));
      try {
        assertTrue(timeoutObserved.await(8, TimeUnit.SECONDS));
      } finally {
        connection.rollback();
      }
      var result = request.get(10, TimeUnit.SECONDS);
      assertFalse(result.liked());
      assertEquals(0, result.likeCount());
    }
    assertEquals(2, transactionIds.size());
    assertNotEquals(transactionIds.get(0), transactionIds.get(1));
    assertCount(0);
  }

  @Test
  void postDeletionAndLikeRequestsCannotLeaveOrphansOrDeadlock() throws Exception {
    long memberId = members.createMember("좋아요 회원").id();
    likes.like(postId, memberId);
    concurrently(
        8,
        index -> {
          if (index == 0) posts.deletePost(postId);
          else {
            try {
              if (index % 2 == 0) likes.like(postId, memberId);
              else likes.unlike(postId, memberId);
            } catch (PostException missing) {
              assertEquals(PostErrorCode.POST_NOT_FOUND, missing.getErrorCode());
            }
          }
          return null;
        });
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM posts", Long.class));
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM post_likes", Long.class));
  }

  @Test
  void databaseConstraintsRejectDuplicateLikesAndNegativeCounts() {
    long memberId = members.createMember("좋아요 회원").id();
    likes.like(postId, memberId);
    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbc.update(
                "INSERT INTO post_likes(post_id, member_id, created_at, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                postId,
                memberId));
    assertThrows(
        DataIntegrityViolationException.class,
        () -> jdbc.update("UPDATE posts SET like_count = -1 WHERE id = ?", postId));
    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbc.update(
                "INSERT INTO post_likes(post_id, member_id, created_at, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                Long.MAX_VALUE,
                memberId));
    assertCount(1);
  }

  private void assertCount(long expected) {
    assertEquals(
        expected,
        jdbc.queryForObject("SELECT like_count FROM posts WHERE id = ?", Long.class, postId));
    assertEquals(
        expected,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM post_likes WHERE post_id = ?", Long.class, postId));
  }

  private <T> List<T> concurrently(int count, Action<T> action) throws Exception {
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
      for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS));
      return results;
    }
  }

  @FunctionalInterface
  interface Action<T> {
    T run(int index) throws Exception;
  }
}
