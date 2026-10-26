package org.sopt.post.adapter.out.persistence;

import java.time.LocalDateTime;
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
  List<Post> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

  @EntityGraph(attributePaths = "member")
  List<Post> findAllByCategoryOrderByCreatedAtDescIdDesc(Category category, Pageable pageable);

  @Query(
      """
      SELECT p FROM Post p JOIN FETCH p.member
      WHERE (p.createdAt, p.id) < (:createdAt, :id)
      ORDER BY p.createdAt DESC, p.id DESC
      """)
  List<Post> findAfter(
      @Param("createdAt") LocalDateTime createdAt, @Param("id") long id, Pageable pageable);

  @Query(
      """
      SELECT p FROM Post p JOIN FETCH p.member
      WHERE p.category = :category
        AND (p.createdAt, p.id) < (:createdAt, :id)
      ORDER BY p.createdAt DESC, p.id DESC
      """)
  List<Post> findAfterByCategory(
      @Param("category") Category category,
      @Param("createdAt") LocalDateTime createdAt,
      @Param("id") long id,
      Pageable pageable);

  @Override
  @Query("SELECT p FROM Post p JOIN FETCH p.member WHERE p.id = :id")
  Optional<Post> findById(@Param("id") Long id);

  @Modifying(flushAutomatically = true)
  @Query(value = "UPDATE posts SET view_count = view_count + 1 WHERE id = :id", nativeQuery = true)
  int incrementViewCount(@Param("id") long id);
}
