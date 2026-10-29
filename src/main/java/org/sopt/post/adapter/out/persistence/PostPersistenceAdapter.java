package org.sopt.post.adapter.out.persistence;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.sopt.post.application.port.out.LoadPostPort;
import org.sopt.post.application.port.out.PostListCriteria;
import org.sopt.post.application.port.out.SavePostPort;
import org.sopt.post.domain.Post;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostPersistenceAdapter implements LoadPostPort, SavePostPort {
  private final PostRepository postRepository;
  private final EntityManager entityManager;

  @Override
  public void save(Post post) {
    if (post.getId() != null) {
      throw new IllegalArgumentException("신규 게시글에는 ID를 지정할 수 없습니다.");
    }
    postRepository.save(post);
  }

  @Override
  public List<Post> findAll(PostListCriteria criteria) {
    return postRepository.findPosts(criteria);
  }

  @Override
  public Optional<Post> findById(long id) {
    return postRepository.findById(id);
  }

  @Override
  public boolean incrementViewCount(long id) {
    if (postRepository.incrementViewCount(id) == 0) {
      return false;
    }
    // 이미 읽은 대상만 refresh해 벌크 UPDATE 결과를 반영하고 다른 엔티티의 변경 감지를 유지합니다.
    // 아직 읽지 않은 프록시는 다음 조회에서 회원과 함께 로드하므로 중복 SELECT를 생략합니다.
    Post reference = entityManager.getReference(Post.class, id);
    if (entityManager.getEntityManagerFactory().getPersistenceUnitUtil().isLoaded(reference)) {
      entityManager.refresh(reference);
    }
    return true;
  }

  @Override
  public void delete(Post post) {
    postRepository.delete(post);
  }
}
