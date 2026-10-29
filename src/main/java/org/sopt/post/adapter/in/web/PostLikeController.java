package org.sopt.post.adapter.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.sopt.global.response.BaseResponse;
import org.sopt.post.adapter.in.web.dto.response.PostLikeResponse;
import org.sopt.post.application.port.in.command.PostLikeUseCase;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "게시글 좋아요", description = "회원별 좋아요 설정·취소")
@ApiResponse(responseCode = "400", ref = "#/components/responses/PostLikeBadRequest")
@ApiResponse(responseCode = "404", ref = "#/components/responses/PostLikeNotFound")
@ApiResponse(responseCode = "503", ref = "#/components/responses/PostLikeBusy")
@ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
@RequestMapping(
    value = "/posts/{postId}/likes/{memberId}",
    produces = MediaType.APPLICATION_JSON_VALUE)
public class PostLikeController {
  private final PostLikeUseCase useCase;

  @PutMapping
  @Operation(
      summary = "게시글 좋아요",
      description = "활성 회원의 좋아요를 설정합니다. 반복 요청해도 한 번만 반영합니다. 현재는 인증 없이 memberId를 명시적으로 전달합니다.")
  @ApiResponse(responseCode = "200", description = "좋아요 설정 완료. 이미 좋아요한 경우도 성공")
  public BaseResponse<PostLikeResponse> like(
      @PathVariable("postId") long postId, @PathVariable("memberId") long memberId) {
    return BaseResponse.success(
        PostLikeResponse.from(useCase.like(postId, memberId)), "좋아요를 설정했습니다.");
  }

  @DeleteMapping
  @Operation(
      summary = "게시글 좋아요 취소",
      description = "활성 회원의 좋아요를 취소합니다. 좋아요하지 않은 경우에도 성공합니다. 탈퇴 회원의 기존 좋아요는 유지합니다.")
  @ApiResponse(responseCode = "200", description = "좋아요 취소 완료. 이미 취소된 경우도 성공")
  public BaseResponse<PostLikeResponse> unlike(
      @PathVariable("postId") long postId, @PathVariable("memberId") long memberId) {
    return BaseResponse.success(
        PostLikeResponse.from(useCase.unlike(postId, memberId)), "좋아요를 취소했습니다.");
  }
}
