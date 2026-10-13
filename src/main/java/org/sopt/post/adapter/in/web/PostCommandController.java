package org.sopt.post.adapter.in.web;

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
@RestController
@RequestMapping("/posts")
public class PostCommandController {
  private final PostCommandUseCase useCase;

  public PostCommandController(PostCommandUseCase useCase) {
    this.useCase = useCase;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public BaseResponse<Void> createPost(@Valid @RequestBody CreatePostRequest request) {
    Category category = request.category() == null ? null : request.category().toDomain();
    useCase.createPost(request.title(), request.content(), category, request.author());
    return BaseResponse.success(null, "게시글이 작성되었습니다.");
  }

  @PutMapping("/{id}")
  public BaseResponse<Void> updatePost(@PathVariable("id") long id,
      @Valid @RequestBody UpdatePostRequest request) {
    useCase.updatePost(id, request.title(), request.content());
    return BaseResponse.success(null, "게시글이 수정되었습니다.");
  }

  @DeleteMapping("/{id}")
  public BaseResponse<Void> deletePost(@PathVariable("id") long id) {
    useCase.deletePost(id);
    return BaseResponse.success(null, "게시글이 삭제되었습니다.");
  }
}
