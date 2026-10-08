package org.sopt.post.domain.exception;

import org.sopt.global.exception.BaseException;

/**
 * 게시글 도메인과 유스케이스에서 발생하는 예상 가능한 실패를 전달합니다.
 * BaseException을 상속해 공통 예외 처리에 참여하고, PostErrorCode만 받도록 실패 종류를 제한합니다.
 */
public class PostException extends BaseException {
  public PostException(PostErrorCode errorCode) {
    super(errorCode);
  }
}
