package org.sopt.post.adapter.out.persistence;

import org.sopt.post.domain.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
  boolean existsByPostIdAndMemberId(long postId, long memberId);

  @Modifying(flushAutomatically = true)
  @Query("DELETE FROM PostLike l WHERE l.post.id = :postId AND l.member.id = :memberId")
  int deleteLike(@Param("postId") long postId, @Param("memberId") long memberId);
}
