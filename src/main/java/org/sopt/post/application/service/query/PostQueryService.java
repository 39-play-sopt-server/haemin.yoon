package org.sopt.post.application.service.query;

import org.sopt.post.application.port.in.query.PostCursor;
import org.sopt.post.application.port.in.query.PostInfo;
import org.sopt.post.application.port.in.query.PostListQuery;
import org.sopt.post.application.port.in.query.PostListResult;
import org.sopt.post.application.port.in.query.PostQueryUseCase;
import org.sopt.post.application.port.out.LoadPostPort;
import org.sopt.post.application.port.out.PostListCriteria;
import org.sopt.post.domain.Post;
import org.sopt.post.domain.exception.PostErrorCode;
import org.sopt.post.domain.exception.PostException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 목록·단건의 순수 조회를 읽기 전용 트랜잭션으로 처리합니다. 조회수는 변경하지 않습니다. */
@Service
@Transactional(readOnly = true)
public class PostQueryService implements PostQueryUseCase {
  private final LoadPostPort loadPostPort;

  public PostQueryService(LoadPostPort loadPostPort) {
    this.loadPostPort = loadPostPort;
  }

  @Override
  public PostListResult getPosts(PostListQuery query) {
    var candidates =
        loadPostPort.findAll(
            new PostListCriteria(
                query.cursor() == null ? null : query.cursor().createdAt(),
                query.cursor() == null ? null : query.cursor().id(),
                query.size(),
                query.category(),
                query.keyword(),
                query.authorId()));
    boolean hasNext = candidates.size() > query.size();
    var posts = candidates.stream().limit(query.size()).toList();
    PostCursor nextCursor = null;
    if (hasNext) {
      Post last = posts.getLast();
      nextCursor =
          new PostCursor(
              last.getCreatedAt(),
              last.getId(),
              query.category(),
              query.keyword(),
              query.authorId());
    }
    return new PostListResult(posts.stream().map(this::toInfo).toList(), hasNext, nextCursor);
  }

  @Override
  public PostInfo getPost(long id) {
    return loadPostPort
        .findById(id)
        .map(this::toInfo)
        .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND));
  }

  private PostInfo toInfo(Post post) {
    return new PostInfo(
        post.getId(),
        post.getTitle(),
        post.getContent(),
        post.getCategory(),
        post.getAuthorMemberId(),
        post.getAuthorName(),
        post.getCreatedAt(),
        post.getUpdatedAt(),
        post.getViewCount(),
        post.getVersion(),
        post.getLikeCount());
  }
}
