package org.sopt.post.application.port.in.query.dto;

import java.util.Objects;
import org.sopt.post.application.port.in.query.PostSearchKeyword;
import org.sopt.post.domain.Category;
import org.sopt.post.domain.exception.PostErrorCode;
import org.sopt.post.domain.exception.PostException;

/** cursor가 null이면 첫 목록입니다. category·keyword·authorId가 null이면 해당 검색 조건을 적용하지 않습니다. */
public record PostListQuery(
    PostCursor cursor, int size, Category category, String keyword, Long authorId) {
  public static final int MAX_SIZE = 100;

  public PostListQuery {
    keyword = PostSearchKeyword.normalize(keyword);
    if (authorId != null && authorId <= 0) {
      throw new PostException(PostErrorCode.INVALID_AUTHOR_ID);
    }
    if (size < 1 || size > MAX_SIZE) {
      throw new PostException(PostErrorCode.INVALID_PAGINATION);
    }
    if (cursor != null
        && (cursor.category() != category
            || !Objects.equals(cursor.keyword(), keyword)
            || !Objects.equals(cursor.authorId(), authorId))) {
      throw new PostException(PostErrorCode.INVALID_CURSOR);
    }
  }
}
