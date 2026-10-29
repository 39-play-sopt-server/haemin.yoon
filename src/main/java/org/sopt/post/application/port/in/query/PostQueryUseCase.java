package org.sopt.post.application.port.in.query;

import org.sopt.post.application.port.in.query.dto.PostInfo;
import org.sopt.post.application.port.in.query.dto.PostListQuery;
import org.sopt.post.application.port.in.query.dto.PostListResult;

/** 게시글 목록·단건의 순수 조회를 공개하는 입력 포트입니다. 조회수 등 상태를 변경하지 않습니다. in은 외부에서 애플리케이션 기능을 호출하는 방향을 뜻합니다. */
public interface PostQueryUseCase {
  /** 커서 다음 목록을 생성일 내림차순, 같은 생성일에서는 ID 내림차순으로 조회합니다. 외부 응답은 웹 어댑터에서 DTO로 변환합니다. */
  PostListResult getPosts(PostListQuery query);

  /** 조회수를 변경하지 않고 게시글을 반환하며, 없으면 POST_NOT_FOUND 예외를 발생시킵니다. */
  PostInfo getPost(long id);
}
