package org.sopt.post.adapter.in.api;

import org.sopt.global.exception.BaseException;
import org.sopt.global.exception.GlobalErrorCode;
import org.sopt.global.exception.GlobalExceptionHandler;
import org.sopt.global.response.BaseResponse;
import org.sopt.post.adapter.in.api.dto.request.CreatePostRequest;
import org.sopt.post.adapter.in.api.dto.request.UpdatePostRequest;
import org.sopt.post.application.port.in.command.PostCommandUseCase;
import org.sopt.post.domain.Category;

/**
 * 작성·수정·삭제 요청을 Command 입력 포트에 연결하는 서버 입력 어댑터입니다.
 * 변경 계약만 주입받아 조회 API와 변경 API의 책임을 구분합니다.
 * 경계의 요청 확인·DTO 변환·공통 응답 처리를 담당하고, 게시글 규칙은 서비스와 도메인에 맡깁니다.
 */
public class PostCommandController {
  private final PostCommandUseCase useCase;
  private final GlobalExceptionHandler exceptionHandler;

  public PostCommandController(PostCommandUseCase useCase, GlobalExceptionHandler exceptionHandler) {
    this.useCase = useCase;
    this.exceptionHandler = exceptionHandler;
  }

  /** 필드 접근 전에 요청 누락을 확인하고, DTO의 메서드로 외부 카테고리를 변환합니다. */
  public BaseResponse<Void> createPost(CreatePostRequest request) {
    return exceptionHandler.handle(() -> {
      requireRequest(request);
      // null은 도메인으로 전달해 필수 카테고리 검증을 한곳에서 수행합니다.
      Category category = request.category() == null ? null : request.category().toDomain();
      useCase.createPost(request.title(), request.content(), category, request.author());
    }, "게시글이 작성되었습니다.");
  }

  public BaseResponse<Void> updatePost(long id, UpdatePostRequest request) {
    return exceptionHandler.handle(() -> {
      requireRequest(request);
      useCase.updatePost(id, request.title(), request.content());
    }, "게시글이 수정되었습니다.");
  }

  public BaseResponse<Void> deletePost(long id) {
    return exceptionHandler.handle(() -> useCase.deletePost(id), "게시글이 삭제되었습니다.");
  }

  /** 요청 누락을 NullPointerException 대신 명확한 INVALID_REQUEST 응답으로 전달합니다. */
  private void requireRequest(Object request) {
    if (request == null) {
      throw new BaseException(GlobalErrorCode.INVALID_REQUEST);
    }
  }
}
