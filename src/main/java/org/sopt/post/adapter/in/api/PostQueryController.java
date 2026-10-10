package org.sopt.post.adapter.in.api;

import java.util.List;
import org.sopt.global.exception.GlobalExceptionHandler;
import org.sopt.global.response.BaseResponse;
import org.sopt.post.adapter.in.api.dto.response.PostResponse;
import org.sopt.post.application.port.in.query.PostQueryUseCase;

/**
 * 조회 요청만 Query 입력 포트에 연결하는 서버 입력 어댑터입니다.
 * Command 포트를 알지 않으므로 게시글 변경 기능을 호출하지 않습니다.
 * 도메인 조회 결과는 DTO의 변환 메서드로 바꾸고, 성공·실패는 공통 응답으로 반환합니다.
 */
public class PostQueryController {
  private final PostQueryUseCase useCase;
  private final GlobalExceptionHandler exceptionHandler;

  public PostQueryController(PostQueryUseCase useCase, GlobalExceptionHandler exceptionHandler) {
    this.useCase = useCase;
    this.exceptionHandler = exceptionHandler;
  }

  /** 불변 DTO와 수정 불가능한 목록으로 반환해 외부에서 서버 상태를 변경하지 못하게 합니다. */
  public BaseResponse<List<PostResponse>> getPosts() {
    return exceptionHandler.handle(
        () -> useCase.getPosts().stream().map(PostResponse::from).toList(),
        "게시글 목록을 조회했습니다.");
  }

  /** 콘솔에서 빈 저장소를 ID 입력 전에 안내하기 위한 조회 기능입니다. */
  public BaseResponse<Boolean> hasPosts() {
    return exceptionHandler.handle(useCase::hasPosts, "게시글 존재 여부를 확인했습니다.");
  }

  public BaseResponse<PostResponse> getPost(long id) {
    return exceptionHandler.handle(() -> PostResponse.from(useCase.getPost(id)), "게시글을 조회했습니다.");
  }
}
