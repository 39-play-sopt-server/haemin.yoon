package org.sopt.post.application.facade;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sopt.post.application.port.in.command.PostCommandUseCase;
import org.sopt.post.application.port.in.query.PostQueryUseCase;
import org.sopt.post.application.port.in.query.dto.PostInfo;
import org.sopt.post.application.port.in.query.dto.PostListQuery;
import org.sopt.post.domain.Category;
import org.sopt.support.MemberDatabaseSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest(
    properties =
        "spring.datasource.url=jdbc:h2:mem:facade-rollback;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Import(PostFacadeTransactionTest.FailingQueryConfiguration.class)
class PostFacadeTransactionTest extends MemberDatabaseSupport {
  @Autowired PostFacade facade;
  @Autowired PostCommandUseCase commands;
  @Autowired JdbcTemplate jdbc;

  @Test
  void failedQueryRollsBackThePrecedingCommandIncrement() {
    commands.createPost("제목", "본문", Category.GENERAL, memberId("작성자"));
    long id = jdbc.queryForObject("SELECT id FROM posts", Long.class);
    assertThrows(IllegalStateException.class, () -> facade.getPostDetail(id));
    assertEquals(
        0L, jdbc.queryForObject("SELECT view_count FROM posts WHERE id = ?", Long.class, id));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FailingQueryConfiguration {
    @Bean
    @Primary
    PostQueryUseCase failingQuery(JdbcTemplate jdbc) {
      return new PostQueryUseCase() {
        @Override
        public org.sopt.post.application.port.in.query.dto.PostListResult getPosts(
            PostListQuery query) {
          return new org.sopt.post.application.port.in.query.dto.PostListResult(
              List.of(), false, null);
        }

        @Override
        public PostInfo getPost(long id) {
          assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
          assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
          assertEquals(
              1L, jdbc.queryForObject("SELECT view_count FROM posts WHERE id = ?", Long.class, id));
          throw new IllegalStateException("상세 조회 실패");
        }
      };
    }
  }
}
