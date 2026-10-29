package org.sopt.post.adapter.in.web.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.sopt.post.application.port.in.command.PostLikeResult;

public record PostLikeResponse(
    @Schema(description = "요청 회원의 처리 후 좋아요 상태") boolean liked,
    @Schema(description = "처리 시점의 게시글 좋아요 수") long likeCount) {
  public static PostLikeResponse from(PostLikeResult result) {
    return new PostLikeResponse(result.liked(), result.likeCount());
  }
}
