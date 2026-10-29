package org.sopt.post.adapter.out.persistence;

import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.sopt.post.domain.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long>, PostListingRepository {
  @Query(value = "SELECT id FROM posts WHERE id = :id FOR UPDATE", nativeQuery = true)
  @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "2000"))
  Optional<Long> lockId(@Param("id") long id);

  @Modifying(flushAutomatically = true)
  @Query(value = "UPDATE posts SET like_count = like_count + 1 WHERE id = :id", nativeQuery = true)
  int increaseLikeCount(@Param("id") long id);

  @Modifying(flushAutomatically = true)
  @Query(value = "UPDATE posts SET like_count = like_count - 1 WHERE id = :id", nativeQuery = true)
  int decreaseLikeCount(@Param("id") long id);

  @Override
  @Query("SELECT p FROM Post p JOIN FETCH p.member WHERE p.id = :id")
  Optional<Post> findById(@Param("id") Long id);

  @Modifying(flushAutomatically = true)
  @Query(value = "UPDATE posts SET view_count = view_count + 1 WHERE id = :id", nativeQuery = true)
  int incrementViewCount(@Param("id") long id);
}
