package org.sopt.post.application.port.in.query.dto;

import java.util.List;

/** 웹 표현에 의존하지 않는 목록 조회 결과입니다. 커서는 마지막 반환 글을 기준으로 합니다. */
public record PostListResult(List<PostInfo> posts, boolean hasNext, PostCursor nextCursor) {
  public PostListResult {
    posts = List.copyOf(posts);
  }
}
