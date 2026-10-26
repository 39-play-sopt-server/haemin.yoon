package org.sopt.post.application.port.in.query;

import java.time.LocalDateTime;
import org.sopt.post.domain.Category;
import org.sopt.post.domain.exception.PostErrorCode;
import org.sopt.post.domain.exception.PostException;

/** 최신순 목록에서 마지막으로 반환한 글의 경계와 검색 범위입니다. 해당 글의 존재 여부와 무관합니다. */
public record PostCursor(LocalDateTime createdAt, long id, Category category) {
  public PostCursor {
    if (createdAt == null || createdAt.getYear() < 1 || createdAt.getYear() > 9999 || id <= 0) {
      throw new PostException(PostErrorCode.INVALID_CURSOR);
    }
  }
}
