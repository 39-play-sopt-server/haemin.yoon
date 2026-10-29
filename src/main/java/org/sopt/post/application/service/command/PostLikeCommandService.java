package org.sopt.post.application.service.command;

import lombok.RequiredArgsConstructor;
import org.sopt.member.application.port.in.query.MemberQueryUseCase;
import org.sopt.post.application.port.in.command.PostLikeCommandUseCase;
import org.sopt.post.application.port.in.command.PostLikeResult;
import org.sopt.post.application.port.out.LoadPostLikePort;
import org.sopt.post.application.port.out.SavePostLikePort;
import org.sopt.post.domain.PostLike;
import org.sopt.post.domain.exception.PostErrorCode;
import org.sopt.post.domain.exception.PostException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 한 요청에서는 게시글 하나만 잠그고, 항상 부모 → 관계 → 카운터 순서로 처리합니다. */
@Service
@RequiredArgsConstructor
@Transactional
public class PostLikeCommandService implements PostLikeCommandUseCase {
  private final MemberQueryUseCase members;
  private final LoadPostLikePort reader;
  private final SavePostLikePort writer;

  @Override
  public PostLikeResult like(long postId, long memberId) {
    validateIds(postId, memberId);
    var member = members.getActiveMember(memberId);
    var post =
        reader.lockPost(postId).orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND));
    if (reader.exists(postId, memberId)) return new PostLikeResult(true, post.getLikeCount());
    writer.save(new PostLike(post, member));
    return new PostLikeResult(true, writer.increaseCount(post));
  }

  @Override
  public PostLikeResult unlike(long postId, long memberId) {
    validateIds(postId, memberId);
    members.getActiveMember(memberId);
    var post =
        reader.lockPost(postId).orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND));
    if (writer.delete(postId, memberId) == 0) return new PostLikeResult(false, post.getLikeCount());
    return new PostLikeResult(false, writer.decreaseCount(post));
  }

  private void validateIds(long postId, long memberId) {
    if (postId <= 0 || memberId <= 0) throw new PostException(PostErrorCode.INVALID_LIKE_ID);
  }
}
