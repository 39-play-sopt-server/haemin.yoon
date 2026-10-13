package org.sopt.post.adapter.in.web;

import java.util.List;
import org.sopt.global.response.BaseResponse;
import org.sopt.post.adapter.in.web.dto.response.PostResponse;
import org.sopt.post.application.port.in.query.PostQueryUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP 조회 요청을 Query 입력 포트에 전달하고 불변 DTO로 응답합니다. */
@RestController
@RequestMapping("/posts")
public class PostQueryController {
  private final PostQueryUseCase useCase;

  public PostQueryController(PostQueryUseCase useCase) {
    this.useCase = useCase;
  }

  @GetMapping
  public BaseResponse<List<PostResponse>> getPosts() {
    return BaseResponse.success(useCase.getPosts().stream().map(PostResponse::from).toList(),
        "게시글 목록을 조회했습니다.");
  }

  @GetMapping("/{id}")
  public BaseResponse<PostResponse> getPost(@PathVariable("id") long id) {
    return BaseResponse.success(PostResponse.from(useCase.getPost(id)), "게시글을 조회했습니다.");
  }
}
