package org.sopt.post.application.service.command;

import org.sopt.global.exception.BaseException;
import org.sopt.global.exception.GlobalErrorCode;
import org.sopt.member.application.port.in.query.MemberQueryUseCase;
import org.sopt.post.application.port.in.command.PostCommandUseCase;
import org.sopt.post.application.port.out.LoadPostPort;
import org.sopt.post.application.port.out.SavePostPort;
import org.sopt.post.domain.Category;
import org.sopt.post.domain.Post;
import org.sopt.post.domain.exception.PostErrorCode;
import org.sopt.post.domain.exception.PostException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시글 생성·수정·삭제·조회수 증가를 담당하는 Command 서비스입니다. 변경 작업도 기존 상태를 읽어야 하므로 조회·변경 포트를 모두 사용합니다. Query 서비스를
 * 호출하지 않고 필요한 조회를 직접 수행해 두 서비스의 책임과 의존성을 분리합니다. 조회부터 변경까지 하나의 트랜잭션에서 수행하며 수정은 JPA 변경 감지로 반영합니다. 유효성
 * 검증은 Post, 생성·삭제와 ID 발급은 출력 어댑터가 담당합니다.
 */
@Service
@Transactional
public class PostCommandService implements PostCommandUseCase {
  private final LoadPostPort loadPostPort;
  private final SavePostPort savePostPort;
  private final MemberQueryUseCase members;

  public PostCommandService(
      LoadPostPort loadPostPort, SavePostPort savePostPort, MemberQueryUseCase members) {
    this.loadPostPort = loadPostPort;
    this.savePostPort = savePostPort;
    this.members = members;
  }

  @Override
  public void createPost(String title, String content, Category category, long memberId) {
    savePostPort.save(new Post(title, content, category, members.getActiveMember(memberId)));
  }

  @Override
  public void updatePost(long id, String title, String content, long expectedVersion) {
    if (expectedVersion < 0) {
      throw new PostException(PostErrorCode.INVALID_VERSION);
    }
    Post original =
        loadPostPort
            .findById(id)
            .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND));
    // 오래된 편집을 거부하고, 이후 실제 동시 경합은 JPA @Version의 커밋 검사에 맡깁니다.
    if (original.getVersion() != expectedVersion) {
      throw new BaseException(GlobalErrorCode.CONFLICT);
    }
    original.update(title, content);
  }

  @Override
  public void deletePost(long id) {
    Post post =
        loadPostPort
            .findById(id)
            .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND));
    savePostPort.delete(post);
  }

  @Override
  public void increaseViewCount(long id) {
    if (!savePostPort.incrementViewCount(id)) {
      throw new PostException(PostErrorCode.POST_NOT_FOUND);
    }
  }
}
