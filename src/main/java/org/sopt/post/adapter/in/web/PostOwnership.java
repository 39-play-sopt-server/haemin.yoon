package org.sopt.post.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.sopt.post.application.port.in.query.PostQueryUseCase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostOwnership {
  private final PostQueryUseCase queries;

  public boolean isAuthor(long postId, long memberId) {
    return queries.getPost(postId).authorId() == memberId;
  }
}
