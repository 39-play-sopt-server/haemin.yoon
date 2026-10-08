package org.sopt.post.application.service;

import java.util.List;
import org.sopt.post.application.port.in.PostUseCase;
import org.sopt.post.application.port.out.PostRepositoryPort;
import org.sopt.post.application.port.out.PostIdGeneratorPort;
import org.sopt.post.domain.Post;
import org.sopt.post.domain.Category;
import org.sopt.post.domain.exception.PostException;
import org.sopt.post.domain.exception.PostErrorCode;

/**
 * 입력 포트의 게시글 유스케이스를 구현하고, 도메인 객체와 출력 포트의 호출 순서를 조율합니다.
 * 게시글 자체의 검증은 Post에, 저장과 ID 발급은 각 포트 구현에 맡깁니다.
 * 입력·출력 형식과 저장 방식에 의존하지 않으며, 실패는 예외로 전달해 서버 경계에서 응답하게 합니다.
 */
public class PostService implements PostUseCase {
  private final PostRepositoryPort repository;
  private final PostIdGeneratorPort idGenerator;

  public PostService(PostRepositoryPort repository, PostIdGeneratorPort idGenerator) {
    this.repository = repository;
    this.idGenerator = idGenerator;
  }

  @Override
  public void createPost(String title, String content, Category category, String author) {
    // 검증보다 ID 발급이 먼저 이루어져 생성 실패에도 ID가 소비될 수 있습니다. 연속 번호는 보장하지 않습니다.
    repository.save(new Post(idGenerator.nextId(), title, content, category, author));
  }

  @Override
  public List<Post> getPosts() {
    return repository.findAll();
  }

  @Override
  public boolean hasPosts() {
    // 목록 조회에 필요한 복사·정렬 없이 존재 여부만 확인합니다.
    return !repository.isEmpty();
  }

  @Override
  public Post getPost(long id) {
    return repository.findById(id)
        .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND));
  }

  @Override
  public void updatePost(long id, String title, String content) {
    // 불변 Post의 수정 결과를 받아 저장소에 반영합니다. 검증에 실패하면 저장소의 update 호출에 도달하지 않습니다.
    Post updated = getPost(id).update(title, content);
    if (!repository.update(updated)) {
      // 조회와 교체는 별도 호출이므로 교체 시점의 대상 존재 여부도 저장소 반환값으로 확인합니다.
      throw new PostException(PostErrorCode.POST_NOT_FOUND);
    }
  }

  @Override
  public void deletePost(long id) {
    if (!repository.deleteById(id)) {
      throw new PostException(PostErrorCode.POST_NOT_FOUND);
    }
  }
}
