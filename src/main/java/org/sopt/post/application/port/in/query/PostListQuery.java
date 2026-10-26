package org.sopt.post.application.port.in.query;

import org.sopt.post.domain.Category;
import org.sopt.post.domain.exception.PostErrorCode;
import org.sopt.post.domain.exception.PostException;

/** 게시글 목록 조회 조건입니다. category가 null이면 전체 분류를 조회합니다. */
public record PostListQuery(int page, int size, Category category) {
  public static final int MAX_SIZE = 100;

  public PostListQuery {
    // JPA의 offset은 int 범위입니다. 곱셈은 long으로 수행해 오버플로도 방지합니다.
    if (page < 0 || size < 1 || size > MAX_SIZE || (long) page * size > Integer.MAX_VALUE) {
      throw new PostException(PostErrorCode.INVALID_PAGINATION);
    }
  }
}
