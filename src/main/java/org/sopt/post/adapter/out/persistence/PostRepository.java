package org.sopt.post.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import org.sopt.post.domain.Category;
import org.sopt.post.domain.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {
  @EntityGraph(attributePaths = "member")
  List<Post> findAllByOrderByIdAsc(Pageable pageable);

  @EntityGraph(attributePaths = "member")
  List<Post> findAllByCategoryOrderByIdAsc(Category category, Pageable pageable);

  @Override
  @Query("SELECT p FROM Post p JOIN FETCH p.member WHERE p.id = :id")
  Optional<Post> findById(@Param("id") Long id);

  @Modifying(flushAutomatically = true)
  @Query(value = "UPDATE posts SET view_count = view_count + 1 WHERE id = :id", nativeQuery = true)
  int incrementViewCount(@Param("id") long id);
}
