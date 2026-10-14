package org.sopt.post.adapter.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.sopt.global.response.BaseResponse;
import org.sopt.post.adapter.in.web.dto.request.CreatePostRequest;
import org.sopt.post.adapter.in.web.dto.request.UpdatePostRequest;
import org.sopt.post.application.port.in.command.PostCommandUseCase;
import org.sopt.post.domain.Category;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** HTTP 입력을 검증하고 Command 입력 포트에 전달합니다. 도메인도 게시글의 불변식을 검증합니다. */
@Tag(name = "게시글", description = "게시글 생성·조회·수정·삭제")
@ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
@RestController
@RequestMapping("/posts")
public class PostCommandController {
  private final PostCommandUseCase useCase;

  public PostCommandController(PostCommandUseCase useCase) {
    this.useCase = useCase;
  }

  @Operation(
      summary = "게시글 생성",
      description = "제목, 본문, 카테고리, 작성자를 입력합니다. ID와 작성 시각은 서버에서 생성하며, 성공 응답의 data는 null입니다.")
  @ApiResponse(responseCode = "201", description = "게시글 생성 성공")
  @ApiResponse(responseCode = "400", ref = "#/components/responses/PostCreateBadRequest")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public BaseResponse<Void> createPost(@Valid @RequestBody CreatePostRequest request) {
    Category category = request.category() == null ? null : request.category().toDomain();
    useCase.createPost(request.title(), request.content(), category, request.author());
    return BaseResponse.success(null, "게시글이 작성되었습니다.");
  }

  @Operation(
      summary = "게시글 수정",
      description = "제목과 본문을 함께 수정합니다. 작성자, 카테고리, 작성 시각은 유지합니다. 성공 응답의 data는 null입니다.")
  @ApiResponse(responseCode = "200", description = "게시글 수정 성공")
  @ApiResponse(responseCode = "400", ref = "#/components/responses/PostUpdateBadRequest")
  @ApiResponse(responseCode = "404", ref = "#/components/responses/PostNotFound")
  @PutMapping("/{id}")
  public BaseResponse<Void> updatePost(
      @Parameter(description = "게시글 ID", example = "1") @PathVariable("id") long id,
      @Valid @RequestBody UpdatePostRequest request) {
    useCase.updatePost(id, request.title(), request.content());
    return BaseResponse.success(null, "게시글이 수정되었습니다.");
  }

  @Operation(summary = "게시글 삭제", description = "게시글을 삭제하고 공통 응답을 반환합니다. 성공 응답의 data는 null입니다.")
  @ApiResponse(responseCode = "200", description = "게시글 삭제 성공")
  @ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidPostId")
  @ApiResponse(responseCode = "404", ref = "#/components/responses/PostNotFound")
  @DeleteMapping("/{id}")
  public BaseResponse<Void> deletePost(
      @Parameter(description = "게시글 ID", example = "1") @PathVariable("id") long id) {
    useCase.deletePost(id);
    return BaseResponse.success(null, "게시글이 삭제되었습니다.");
  }
}
