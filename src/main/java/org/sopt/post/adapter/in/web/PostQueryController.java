package org.sopt.post.adapter.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.sopt.global.response.BaseResponse;
import org.sopt.post.adapter.in.web.dto.response.PostResponse;
import org.sopt.post.application.port.in.query.PostQueryUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP 조회 요청을 Query 입력 포트에 전달하고 불변 DTO로 응답합니다. */
@Tag(name = "게시글", description = "게시글 생성·조회·수정·삭제")
@ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
@RestController
@RequestMapping("/posts")
public class PostQueryController {
  private final PostQueryUseCase useCase;

  public PostQueryController(PostQueryUseCase useCase) {
    this.useCase = useCase;
  }

  @Operation(
      summary = "게시글 목록 조회",
      description = "ID 오름차순으로 전체 게시글을 조회합니다. 게시글이 없으면 data는 빈 배열입니다.")
  @ApiResponse(responseCode = "200", description = "게시글 목록 조회 성공")
  @GetMapping
  public BaseResponse<List<PostResponse>> getPosts() {
    return BaseResponse.success(
        useCase.getPosts().stream().map(PostResponse::from).toList(), "게시글 목록을 조회했습니다.");
  }

  @Operation(summary = "게시글 단건 조회")
  @ApiResponse(responseCode = "200", description = "게시글 조회 성공")
  @ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidPostId")
  @ApiResponse(responseCode = "404", ref = "#/components/responses/PostNotFound")
  @GetMapping("/{id}")
  public BaseResponse<PostResponse> getPost(
      @Parameter(description = "게시글 ID", example = "1") @PathVariable("id") long id) {
    return BaseResponse.success(PostResponse.from(useCase.getPost(id)), "게시글을 조회했습니다.");
  }
}
