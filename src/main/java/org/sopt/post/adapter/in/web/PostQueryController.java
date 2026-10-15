package org.sopt.post.adapter.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.sopt.global.response.BaseResponse;
import org.sopt.post.adapter.in.web.dto.PostCategory;
import org.sopt.post.adapter.in.web.dto.response.PostResponse;
import org.sopt.post.application.facade.PostFacade;
import org.sopt.post.application.port.in.query.PostListQuery;
import org.sopt.post.application.port.in.query.PostQueryUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 목록은 Query 입력 포트에, 상세 열람은 Facade에 전달하고 불변 DTO로 응답합니다. */
@Tag(name = "게시글", description = "게시글 생성·조회·수정·삭제")
@ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
@RestController
@RequestMapping("/posts")
public class PostQueryController {
  private final PostQueryUseCase useCase;
  private final PostFacade facade;

  public PostQueryController(PostQueryUseCase useCase, PostFacade facade) {
    this.useCase = useCase;
    this.facade = facade;
  }

  @Operation(
      summary = "게시글 목록 조회",
      description =
          "카테고리로 필터링한 뒤 ID 오름차순으로 한 페이지를 조회합니다. page는 0부터 시작하며 기본값은 0, size의 기본값은 20이고 허용 범위는 1~100입니다. category를 생략하거나 빈 값으로 보내면 전체 분류를 조회합니다. 결과가 없거나 마지막 페이지를 넘으면 data는 빈 배열입니다.")
  @ApiResponse(responseCode = "200", description = "게시글 목록 조회 성공")
  @ApiResponse(responseCode = "400", ref = "#/components/responses/PostListBadRequest")
  @GetMapping
  public BaseResponse<List<PostResponse>> getPosts(
      @Parameter(description = "0부터 시작하는 페이지 번호", example = "0")
          @RequestParam(name = "page", defaultValue = "0")
          int page,
      @Parameter(description = "페이지당 게시글 수 (1~100)", example = "20")
          @RequestParam(name = "size", defaultValue = "20")
          int size,
      @Parameter(description = "조회할 카테고리 (생략 시 전체)")
          @RequestParam(name = "category", required = false)
          PostCategory category) {
    var query = new PostListQuery(page, size, category == null ? null : category.toDomain());
    return BaseResponse.success(
        useCase.getPosts(query).stream().map(PostResponse::from).toList(), "게시글 목록을 조회했습니다.");
  }

  @Operation(
      summary = "게시글 단건 조회",
      description = "상세 조회 성공마다 조회수가 1 증가하며 증가한 값을 반환합니다. 동일 사용자의 반복 조회도 집계합니다.")
  @ApiResponse(responseCode = "200", description = "게시글 조회 성공")
  @ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidPostId")
  @ApiResponse(responseCode = "404", ref = "#/components/responses/PostNotFound")
  @GetMapping("/{id}")
  public BaseResponse<PostResponse> getPost(
      @Parameter(description = "게시글 ID", example = "1") @PathVariable("id") long id) {
    return BaseResponse.success(PostResponse.from(facade.getPostDetail(id)), "게시글을 조회했습니다.");
  }
}
