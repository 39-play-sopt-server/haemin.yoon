package org.sopt.post.application.facade;

import org.sopt.post.application.port.in.command.PostCommandUseCase;
import org.sopt.post.application.port.in.query.PostDetailQueryUseCase;
import org.sopt.post.application.port.in.query.PostInfo;
import org.sopt.post.application.port.in.query.PostQueryUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Command·Query를 조합하는 상세 열람 흐름과 전체 트랜잭션을 담당합니다. */
@Service
public class PostFacade implements PostDetailQueryUseCase {
  private final PostCommandUseCase commands;
  private final PostQueryUseCase queries;

  public PostFacade(PostCommandUseCase commands, PostQueryUseCase queries) {
    this.commands = commands;
    this.queries = queries;
  }

  /** 조회수 증가와 조회를 하나의 트랜잭션으로 처리하고 증가한 값을 반환합니다. */
  @Transactional
  @Override
  public PostInfo getPostDetail(long id) {
    commands.increaseViewCount(id);
    return queries.getPost(id);
  }
}
