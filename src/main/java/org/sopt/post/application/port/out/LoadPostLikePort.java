package org.sopt.post.application.port.out;

import java.util.Optional;
import org.sopt.post.domain.Post;

public interface LoadPostLikePort {
  /**
   * 관계 행에 접근하기 전에 잠금 대기 제한을 설정하고 게시글만 배타 잠금합니다. 회원 행에는 잠금을 전파하지 않습니다. 이후 좋아요 쓰기도 같은 트랜잭션에서 실행해야
   * 합니다.
   */
  Optional<Post> lockPost(long postId);

  boolean exists(long postId, long memberId);
}
