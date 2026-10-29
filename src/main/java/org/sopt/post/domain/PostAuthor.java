package org.sopt.post.domain;

import org.sopt.post.domain.exception.PostErrorCode;
import org.sopt.post.domain.exception.PostException;

/** 게시글이 소유하는 작성자 식별자와 게시 시점 이름입니다. */
public record PostAuthor(long memberId, String name) {
  public PostAuthor {
    if (memberId <= 0 || name == null || name.isBlank()) {
      throw new PostException(PostErrorCode.INVALID_AUTHOR);
    }
  }
}
