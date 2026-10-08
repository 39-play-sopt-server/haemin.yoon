package org.sopt.post.adapter.in.api;

import java.util.List;
import org.sopt.global.exception.BaseException;
import org.sopt.global.exception.GlobalErrorCode;
import org.sopt.global.exception.GlobalExceptionHandler;
import org.sopt.global.response.BaseResponse;
import org.sopt.post.adapter.in.api.dto.request.CreatePostRequest;
import org.sopt.post.adapter.in.api.dto.request.UpdatePostRequest;
import org.sopt.post.adapter.in.api.dto.response.PostResponse;
import org.sopt.post.application.port.in.PostUseCase;
import org.sopt.post.domain.Category;

/**
 * 외부 요청을 애플리케이션의 입력 포트로 연결하는 서버 입력 어댑터입니다.
 * DTO의 변환 메서드를 사용해 요청을 도메인 값으로 바꾸고, 결과를 응답 DTO와 공통 응답으로 돌려줍니다.
 * 현재 콘솔이 직접 호출하며, 콘솔 입력이나 저장소 구현은 이곳에서 다루지 않습니다.
 */
public class PostController {
  private final PostUseCase useCase;
  private final GlobalExceptionHandler exceptionHandler;

  public PostController(PostUseCase useCase, GlobalExceptionHandler exceptionHandler) {
    this.useCase = useCase;
    this.exceptionHandler = exceptionHandler;
  }

  /** 경계의 요청 객체를 확인한 뒤, 게시글 내용의 유효성 검증은 유스케이스와 도메인에 맡깁니다. */
  public BaseResponse<Void> createPost(CreatePostRequest request) {
    return exceptionHandler.handle(() -> {
      requireRequest(request);
      // null은 도메인으로 전달해 필수 카테고리 검증을 한곳에서 수행합니다.
      Category category = request.category() == null ? null : request.category().toDomain();
      useCase.createPost(request.title(), request.content(), category, request.author());
    }, "게시글이 작성되었습니다.");
  }

  /** 불변 응답 DTO와 수정 불가능한 목록으로 반환해 서버 내부 객체가 외부에 노출되지 않게 합니다. */
  public BaseResponse<List<PostResponse>> getPosts() {
    return exceptionHandler.handle(
        () -> useCase.getPosts().stream().map(PostResponse::from).toList(),
        "게시글 목록을 조회했습니다.");
  }

  public BaseResponse<Boolean> hasPosts() {
    return exceptionHandler.handle(useCase::hasPosts, "게시글 존재 여부를 확인했습니다.");
  }

  public BaseResponse<PostResponse> getPost(long id) {
    return exceptionHandler.handle(() -> PostResponse.from(useCase.getPost(id)), "게시글을 조회했습니다.");
  }

  public BaseResponse<Void> updatePost(long id, UpdatePostRequest request) {
    return exceptionHandler.handle(() -> {
      requireRequest(request);
      useCase.updatePost(id, request.title(), request.content());
    }, "게시글이 수정되었습니다.");
  }

  public BaseResponse<Void> deletePost(long id) {
    return exceptionHandler.handle(() -> {
      useCase.deletePost(id);
    }, "게시글이 삭제되었습니다.");
  }

  /** 요청 자체의 누락을 필드 접근 전 확인해 NullPointerException 대신 명확한 오류를 응답합니다. */
  private void requireRequest(Object request) {
    if (request == null) {
      throw new BaseException(GlobalErrorCode.INVALID_REQUEST);
    }
  }

}
