package org.sopt.post.adapter.out.persistence;

import java.util.List;
import org.sopt.post.domain.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {
  List<Post> findAllByOrderByIdAsc();

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(value = "UPDATE posts SET view_count = view_count + 1 WHERE id = :id", nativeQuery = true)
  int incrementViewCount(@Param("id") long id);
}
