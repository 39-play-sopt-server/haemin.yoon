package org.sopt.post.domain.exception;

import org.sopt.global.exception.ErrorCode;
import org.sopt.post.domain.Post;
import org.springframework.http.HttpStatus;

/**
 * 게시글 처리의 예상 가능한 실패를 코드와 안내 메시지로 정의합니다. 오류마다 예외 클래스를 만들지 않고, 하나의 PostException에 이 코드를 담아 실패 종류를
 * 구분합니다.
 */
public enum PostErrorCode implements ErrorCode {
  POST_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "존재하지 않는 게시글입니다."),
  INVALID_TITLE(HttpStatus.BAD_REQUEST, "INVALID_TITLE", "제목은 비어 있을 수 없습니다."),
  INVALID_CONTENT(HttpStatus.BAD_REQUEST, "INVALID_CONTENT", "본문은 비어 있을 수 없습니다."),
  NUL_CHARACTER_NOT_ALLOWED(
      HttpStatus.BAD_REQUEST, "NUL_CHARACTER_NOT_ALLOWED", "게시글 텍스트에는 NUL 문자를 포함할 수 없습니다."),
  TITLE_TOO_LONG(
      HttpStatus.BAD_REQUEST, "TITLE_TOO_LONG", "제목은 " + Post.MAX_TITLE_LENGTH + "자를 초과할 수 없습니다."),
  CONTENT_TOO_LONG(
      HttpStatus.BAD_REQUEST,
      "CONTENT_TOO_LONG",
      "본문은 " + Post.MAX_CONTENT_LENGTH + "자를 초과할 수 없습니다."),
  INVALID_CATEGORY(HttpStatus.BAD_REQUEST, "INVALID_CATEGORY", "카테고리는 필수입니다."),
  INVALID_PAGINATION(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "목록 크기가 허용 범위를 벗어났습니다."),
  INVALID_CURSOR(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "유효하지 않은 게시글 커서입니다."),
  INVALID_VERSION(HttpStatus.BAD_REQUEST, "INVALID_VERSION", "게시글 버전은 0 이상이어야 합니다."),
  INVALID_AUTHOR(HttpStatus.BAD_REQUEST, "INVALID_AUTHOR", "작성자는 활성 회원이어야 합니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;

  PostErrorCode(HttpStatus httpStatus, String code, String message) {
    this.httpStatus = httpStatus;
    this.code = code;
    this.message = message;
  }

  @Override
  public HttpStatus getHttpStatus() {
    return httpStatus;
  }

  @Override
  public String getCode() {
    return code;
  }

  @Override
  public String getMessage() {
    return message;
  }
}
