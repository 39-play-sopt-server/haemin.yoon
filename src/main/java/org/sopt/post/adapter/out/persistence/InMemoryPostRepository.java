package org.sopt.post.adapter.out.persistence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.sopt.post.application.port.out.PostRepositoryPort;
import org.sopt.post.domain.Post;

/**
 * 저장소 출력 포트를 구현하며, HashMap으로 게시글의 저장과 ID 접근을 담당합니다.
 * ID 조회·교체·삭제는 평균 O(1)이며, 목록 정렬은 별도로 수행합니다.
 * 단일 스레드 콘솔 실행용 메모리 저장소이므로 재시작하면 게시글이 사라집니다.
 */
public class InMemoryPostRepository implements PostRepositoryPort {
  private final Map<Long, Post> posts = new HashMap<>();

  @Override
  public void save(Post post) {
    // 신규 저장과 수정을 구분해 ID 충돌로 기존 게시글이 덮어써지지 않게 합니다.
    if (posts.putIfAbsent(post.getId(), post) != null) {
      throw new IllegalStateException("이미 사용 중인 게시글 ID입니다: " + post.getId());
    }
  }

  @Override
  public List<Post> findAll() {
    // HashMap의 순회 순서는 보장되지 않으므로 독립된 목록을 만든 뒤 포트의 정렬 계약을 지킵니다.
    List<Post> result = new ArrayList<>(posts.values());
    result.sort(Comparator.comparingLong(Post::getId));
    return result;
  }

  @Override
  public boolean isEmpty() {
    return posts.isEmpty();
  }

  @Override
  public Optional<Post> findById(long id) {
    return Optional.ofNullable(posts.get(id));
  }

  @Override
  public boolean update(Post post) {
    // replace는 대상이 없을 때 새 글을 삽입하지 않으며, 교체 여부를 서비스에 전달합니다.
    return posts.replace(post.getId(), post) != null;
  }

  @Override
  public boolean deleteById(long id) {
    return posts.remove(id) != null;
  }
}
