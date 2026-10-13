package org.sopt.post.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import org.sopt.post.application.port.out.LoadPostPort;
import org.sopt.post.application.port.out.SavePostPort;
import org.sopt.post.domain.Post;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PostPersistenceAdapter implements LoadPostPort, SavePostPort {
  private final PostRepository postRepository;

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
  public void delete(Post post) {
    postRepository.delete(post);
  }
}
