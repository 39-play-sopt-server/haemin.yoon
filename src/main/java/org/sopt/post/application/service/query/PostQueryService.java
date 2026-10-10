package org.sopt.post.application.service.query;

import java.util.List;
import org.sopt.post.application.port.in.query.PostQueryUseCase;
import org.sopt.post.application.port.out.LoadPostPort;
import org.sopt.post.domain.Post;
import org.sopt.post.domain.exception.PostException;
import org.sopt.post.domain.exception.PostErrorCode;

/**
 * 상태를 변경하지 않는 게시글 조회 흐름을 구현하는 Query 서비스입니다.
 * LoadPostPort만 주입받아 변경 포트나 ID 발급기를 사용할 필요가 없습니다.
 * 이후 검색·필터 같은 조회 요구를 Command 처리와 독립적으로 확장할 수 있습니다.
 */
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
  public boolean hasPosts() {
    // 목록 복사·정렬 없이 존재 여부만 확인합니다.
    return !loadPostPort.isEmpty();
  }

  @Override
  public Post getPost(long id) {
    return loadPostPort.findById(id)
        .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND));
  }
}
