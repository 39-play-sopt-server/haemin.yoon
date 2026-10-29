package org.sopt.post.application.port.in.command;

public interface PostLikeCommandUseCase {
  PostLikeResult like(long postId, long memberId);

  PostLikeResult unlike(long postId, long memberId);
}
