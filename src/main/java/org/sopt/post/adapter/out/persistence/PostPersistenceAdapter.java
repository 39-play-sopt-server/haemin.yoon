package org.sopt.post.adapter.out.persistence;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.sopt.post.application.port.out.LoadPostPort;
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
  public List<Post> findAll() {
    return postRepository.findAllByOrderByIdAsc();
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
    // 벌크 UPDATE 전 flush로 미반영 수정을 보존하고, 대상만 갱신해 다른 엔티티의 변경 감지를 유지한다.
    // 참조 획득은 SELECT하지 않으며, refresh에서 대상만 한 번 읽는다.
    entityManager.refresh(entityManager.getReference(Post.class, id));
    return true;
  }

  @Override
  public void delete(Post post) {
    postRepository.delete(post);
  }
}
