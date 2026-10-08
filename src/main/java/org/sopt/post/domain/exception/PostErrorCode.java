package org.sopt.post.domain.exception;

import org.sopt.global.exception.ErrorCode;

/**
 * 게시글 처리의 예상 가능한 실패를 코드와 안내 메시지로 정의합니다.
 * 오류마다 예외 클래스를 만들지 않고, 하나의 PostException에 이 코드를 담아 실패 종류를 구분합니다.
 */
public enum PostErrorCode implements ErrorCode {
  POST_NOT_FOUND("존재하지 않는 게시글입니다."),
  INVALID_TITLE("제목은 비어 있을 수 없습니다."),
  INVALID_CONTENT("본문은 비어 있을 수 없습니다."),
  INVALID_CATEGORY("카테고리는 필수입니다."),
  INVALID_AUTHOR("작성자는 비어 있을 수 없습니다.");

  private final String message;

  PostErrorCode(String message) {
    this.message = message;
  }

  @Override
  public String getCode() {
    // Enum 이름이 클라이언트에 전달되는 오류 코드이므로 이름 변경은 응답 계약 변경에 해당합니다.
    return name();
  }

  @Override
  public String getMessage() {
    return message;
  }
}
