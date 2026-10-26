package org.sopt.post.application.port.in.query;

import org.sopt.post.domain.Category;
import org.sopt.post.domain.exception.PostErrorCode;
import org.sopt.post.domain.exception.PostException;

/** cursor가 null이면 첫 목록, category가 null이면 전체 분류를 조회합니다. */
public record PostListQuery(PostCursor cursor, int size, Category category) {
  public static final int MAX_SIZE = 100;

  public PostListQuery {
    if (size < 1 || size > MAX_SIZE) {
      throw new PostException(PostErrorCode.INVALID_PAGINATION);
    }
    if (cursor != null && cursor.category() != category) {
      throw new PostException(PostErrorCode.INVALID_CURSOR);
    }
  }
}
