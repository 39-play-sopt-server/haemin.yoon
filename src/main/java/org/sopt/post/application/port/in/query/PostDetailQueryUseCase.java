package org.sopt.post.application.port.in.query;

/** 조회수 증가를 포함한 게시글 상세 열람 계약입니다. */
public interface PostDetailQueryUseCase {
  PostInfo getPostDetail(long id);
}
