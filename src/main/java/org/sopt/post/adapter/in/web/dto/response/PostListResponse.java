package org.sopt.post.adapter.in.web.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.sopt.post.adapter.in.web.dto.PostCursorCodec;
import org.sopt.post.application.port.in.query.dto.PostListResult;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PostListResponse(
    @Schema(description = "생성일·ID 내림차순 게시글 목록") List<PostResponse> items,
    @Schema(description = "이어서 조회할 게시글이 있는지 여부") boolean hasNext,
    @Schema(description = "다음 요청에 그대로 전달할 커서. 마지막 목록에서는 null", nullable = true) String nextCursor) {
  public PostListResponse {
    items = List.copyOf(items);
  }

  public static PostListResponse from(PostListResult result) {
    return new PostListResponse(
        result.posts().stream().map(PostResponse::from).toList(),
        result.hasNext(),
        PostCursorCodec.encode(result.nextCursor()));
  }
}
