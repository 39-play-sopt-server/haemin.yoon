package org.sopt.post.application.port.out;

import org.sopt.post.domain.Post;
import org.sopt.post.domain.PostLike;

/** 호출자는 게시글 잠금을 보유하고 관계 변경과 카운터 변경을 같은 트랜잭션에서 수행해야 합니다. */
public interface SavePostLikePort {
  void save(PostLike like);

  int delete(long postId, long memberId);

  long increaseCount(Post post);

  long decreaseCount(Post post);
}
