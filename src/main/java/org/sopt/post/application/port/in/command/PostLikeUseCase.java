package org.sopt.post.application.port.in.command;

import org.sopt.post.application.port.in.command.dto.PostLikeResult;

/** 잠금 충돌 재시도 정책까지 포함해 외부에 공개하는 게시글 좋아요 계약입니다. */
public interface PostLikeUseCase {
  PostLikeResult like(long postId, long memberId);

  PostLikeResult unlike(long postId, long memberId);
}
