package org.sopt.post.adapter.out.persistence;

import java.util.List;
import org.sopt.post.domain.Post;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {
  List<Post> findAllByOrderByIdAsc();
}
