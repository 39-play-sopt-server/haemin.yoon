package org.sopt.post.application.service.command;

import org.sopt.post.application.port.in.command.PostCommandUseCase;
import org.sopt.post.application.port.out.LoadPostPort;
import org.sopt.post.application.port.out.SavePostPort;
import org.sopt.post.application.port.out.PostIdGeneratorPort;
import org.sopt.post.domain.Category;
import org.sopt.post.domain.Post;
import org.sopt.post.domain.exception.PostException;
import org.sopt.post.domain.exception.PostErrorCode;

/**
 * 게시글 생성·수정·삭제 흐름을 조율하는 Command 서비스입니다.
 * 변경 작업도 기존 상태를 읽어야 하므로 조회·변경 포트를 모두 사용합니다.
 * Query 서비스를 호출하지 않고 필요한 조회를 직접 수행해 두 서비스의 책임과 의존성을 분리합니다.
 * 유효성 검증은 Post, 실제 저장과 ID 발급은 출력 어댑터가 담당합니다.
 */
public class PostCommandService implements PostCommandUseCase {
  private final LoadPostPort loadPostPort;
  private final SavePostPort savePostPort;
  private final PostIdGeneratorPort idGenerator;

  public PostCommandService(LoadPostPort loadPostPort, SavePostPort savePostPort,
      PostIdGeneratorPort idGenerator) {
    this.loadPostPort = loadPostPort;
    this.savePostPort = savePostPort;
    this.idGenerator = idGenerator;
  }

  @Override
  public void createPost(String title, String content, Category category, String author) {
    // 검증보다 먼저 ID를 발급하므로 생성 실패 시에도 ID가 소비될 수 있습니다.
    savePostPort.save(new Post(idGenerator.nextId(), title, content, category, author));
  }

  @Override
  public void updatePost(long id, String title, String content) {
    Post original = loadPostPort.findById(id)
        .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND));
    // 불변 객체를 새로 만들며, 검증 실패 시 저장소의 update 호출까지 진행하지 않습니다.
    Post updated = original.update(title, content);
    // 조회 후에도 교체 성공 여부를 확인해, 없는 ID로 새 게시글을 삽입하지 않습니다.
    if (!savePostPort.update(updated)) {
      throw new PostException(PostErrorCode.POST_NOT_FOUND);
    }
  }

  @Override
  public void deletePost(long id) {
    if (!savePostPort.deleteById(id)) {
      throw new PostException(PostErrorCode.POST_NOT_FOUND);
    }
  }
}
