package org.sopt.post.application.service.query;

import java.util.List;
import org.sopt.post.application.port.in.query.PostQueryUseCase;
import org.sopt.post.application.port.out.LoadPostPort;
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
  public List<Post> getPosts() {
    return loadPostPort.findAll();
  }

  @Override
  public Post getPost(long id) {
    return loadPostPort
        .findById(id)
        .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND));
  }
}
