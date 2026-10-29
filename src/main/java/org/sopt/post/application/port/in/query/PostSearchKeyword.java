package org.sopt.post.application.port.in.query;

import org.sopt.post.domain.Post;
import org.sopt.post.domain.exception.PostErrorCode;
import org.sopt.post.domain.exception.PostException;

/** 목록 조건과 커서가 공유하는 제목 검색어 정규화 정책입니다. SQL 표현은 영속성 어댑터가 담당합니다. */
public final class PostSearchKeyword {
  private PostSearchKeyword() {}

  public static String normalize(String keyword) {
    if (keyword == null) return null;
    String normalized = keyword.strip();
    if (normalized.isEmpty()) return null;
    if (normalized.length() > Post.MAX_TITLE_LENGTH || normalized.indexOf('\0') >= 0) {
      throw new PostException(PostErrorCode.INVALID_SEARCH_KEYWORD);
    }
    return normalized;
  }
}
