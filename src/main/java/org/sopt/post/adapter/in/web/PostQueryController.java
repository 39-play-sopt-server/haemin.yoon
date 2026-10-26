package org.sopt.post.adapter.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.sopt.global.response.BaseResponse;
import org.sopt.post.adapter.in.web.dto.PostCategory;
import org.sopt.post.adapter.in.web.dto.PostCursorCodec;
import org.sopt.post.adapter.in.web.dto.response.PostListResponse;
import org.sopt.post.adapter.in.web.dto.response.PostResponse;
import org.sopt.post.application.facade.PostFacade;
import org.sopt.post.application.port.in.query.PostListQuery;
import org.sopt.post.application.port.in.query.PostQueryUseCase;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 목록은 Query 입력 포트에, 상세 열람은 Facade에 전달하고 불변 DTO로 응답합니다. */
@Tag(name = "게시글", description = "게시글 생성·조회·수정·삭제")
@ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
@RestController
@RequestMapping(value = "/posts", produces = MediaType.APPLICATION_JSON_VALUE)
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
          "생성일·ID 내림차순으로 조회합니다. 첫 요청은 cursor를 생략하고 이후 응답의 nextCursor를 그대로 전달합니다. size 기본값은 20이며 1~100입니다. category를 바꾸면 cursor를 생략하고 다시 시작합니다. data.items가 목록이며 마지막 목록은 hasNext=false, nextCursor=null입니다.")
  @ApiResponse(responseCode = "200", description = "게시글 목록 조회 성공")
  @ApiResponse(responseCode = "400", ref = "#/components/responses/PostListBadRequest")
  @GetMapping
  public BaseResponse<PostListResponse> getPosts(
      @Parameter(description = "이전 응답의 nextCursor. 생략 또는 빈 문자열이면 첫 목록")
          @RequestParam(name = "cursor", required = false)
          String cursor,
      @Parameter(description = "한 번에 조회할 게시글 수 (1~100)", example = "20")
          @RequestParam(name = "size", defaultValue = "20")
          int size,
      @Parameter(description = "조회할 카테고리 (생략 시 전체)")
          @RequestParam(name = "category", required = false)
          PostCategory category) {
    var query =
        new PostListQuery(
            PostCursorCodec.decode(cursor), size, category == null ? null : category.toDomain());
    return BaseResponse.success(PostListResponse.from(useCase.getPosts(query)), "게시글 목록을 조회했습니다.");
  }

  @Operation(
      summary = "게시글 단건 조회",
      description = "상세 조회 성공마다 조회수가 1 증가하며 증가한 값을 반환합니다. 동일 사용자의 반복 조회도 집계합니다.")
  @ApiResponse(
      responseCode = "200",
      description = "게시글 조회 성공",
      headers =
          @Header(
              name = HttpHeaders.CACHE_CONTROL,
              description = "상세 응답을 캐시에 저장하지 않습니다.",
              schema = @Schema(type = "string", example = "no-store")))
  @ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidPostId")
  @ApiResponse(responseCode = "404", ref = "#/components/responses/PostNotFound")
  @GetMapping("/{id}")
  public ResponseEntity<BaseResponse<PostResponse>> getPost(
      @Parameter(description = "게시글 ID", example = "1") @PathVariable("id") long id) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(BaseResponse.success(PostResponse.from(facade.getPostDetail(id)), "게시글을 조회했습니다."));
  }

  @Operation(summary = "게시글 응답 헤더 조회", description = "조회수를 증가시키지 않고 게시글을 확인합니다. 응답 본문은 전송하지 않습니다.")
  @ApiResponse(
      responseCode = "200",
      description = "게시글 확인 성공. 응답 본문 없음",
      content = @Content,
      headers =
          @Header(
              name = HttpHeaders.CACHE_CONTROL,
              description = "상세 응답을 캐시에 저장하지 않습니다.",
              schema = @Schema(type = "string", example = "no-store")))
  @ApiResponse(responseCode = "400", description = "게시글 ID 형식 오류. 응답 본문 없음", content = @Content)
  @ApiResponse(responseCode = "404", description = "게시글이 없음. 응답 본문 없음", content = @Content)
  @RequestMapping(value = "/{id}", method = RequestMethod.HEAD)
  public ResponseEntity<BaseResponse<PostResponse>> headPost(
      @Parameter(description = "게시글 ID", example = "1") @PathVariable("id") long id) {
    // GET과 같은 응답 표현을 만들고, 서블릿 컨테이너가 HEAD 본문을 생략합니다.
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(BaseResponse.success(PostResponse.from(useCase.getPost(id)), "게시글을 조회했습니다."));
  }
}
