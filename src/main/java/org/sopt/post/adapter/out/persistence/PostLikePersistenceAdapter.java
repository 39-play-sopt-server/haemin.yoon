package org.sopt.post.adapter.out.persistence;

import jakarta.persistence.EntityManager;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.sopt.post.application.port.out.LoadPostLikePort;
import org.sopt.post.application.port.out.SavePostLikePort;
import org.sopt.post.domain.Post;
import org.sopt.post.domain.PostLike;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostLikePersistenceAdapter implements LoadPostLikePort, SavePostLikePort {
  private final PostRepository posts;
  private final PostLikeRepository likes;
  private final EntityManager entityManager;

  @Override
  public Optional<Post> lockPost(long postId) {
    configureLockWaitTimeout();
    // JOIN FETCH에 FOR UPDATE를 붙여 작성자까지 잠그지 않도록 부모 행만 먼저 잠급니다.
    if (posts.lockId(postId).isEmpty()) return Optional.empty();
    return posts.findById(postId);
  }

  /** 부모 잠금뿐 아니라 이후 관계 INSERT/DELETE와 카운터 갱신의 암묵적 잠금에도 적용합니다. */
  private void configureLockWaitTimeout() {
    var dialect =
        entityManager
            .getEntityManagerFactory()
            .unwrap(SessionFactoryImplementor.class)
            .getJdbcServices()
            .getDialect();
    if (dialect instanceof PostgreSQLDialect) {
      // LOCAL은 현재 트랜잭션의 커밋·롤백 시 복원되어 풀의 다음 사용자에게 전파되지 않습니다.
      entityManager.createNativeQuery("SET LOCAL lock_timeout = '2s'").executeUpdate();
    }
  }

  @Override
  public boolean exists(long postId, long memberId) {
    return likes.existsByPostIdAndMemberId(postId, memberId);
  }

  @Override
  public void save(PostLike like) {
    likes.saveAndFlush(like);
  }

  @Override
  public int delete(long postId, long memberId) {
    return likes.deleteLike(postId, memberId);
  }

  @Override
  public long increaseCount(Post post) {
    posts.increaseLikeCount(post.getId());
    entityManager.refresh(post);
    return post.getLikeCount();
  }

  @Override
  public long decreaseCount(Post post) {
    posts.decreaseLikeCount(post.getId());
    entityManager.refresh(post);
    return post.getLikeCount();
  }
}
