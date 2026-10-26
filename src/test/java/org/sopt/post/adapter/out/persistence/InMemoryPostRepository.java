package org.sopt.post.adapter.out.persistence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.sopt.post.application.port.in.query.PostListQuery;
import org.sopt.post.application.port.out.LoadPostPort;
import org.sopt.post.application.port.out.SavePostPort;
import org.sopt.post.domain.Post;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 서비스·웹 단위 테스트용 저장소 대역입니다. 운영 서버에는 등록하지 않습니다. 같은 엔티티 참조를 반환해 서비스의 엔티티 변경을 관찰합니다. 트랜잭션·변경 감지·롤백은 실제
 * DB 통합 테스트에서 검증합니다.
 */
public class InMemoryPostRepository implements LoadPostPort, SavePostPort {
  private final AtomicLong lastId = new AtomicLong();
  private final Map<Long, Post> posts = new ConcurrentHashMap<>();

  @Override
  public void save(Post post) {
    if (post.getId() != null) {
      throw new IllegalArgumentException("신규 게시글에는 ID를 지정할 수 없습니다.");
    }
    // DB 자동 ID 부여만 대체하며, 테스트를 위한 ID 생성 API를 운영 엔티티에 추가하지 않습니다.
    ReflectionTestUtils.setField(post, "id", lastId.incrementAndGet());
    posts.put(post.getId(), post);
  }

  @Override
  public List<Post> findAll(PostListQuery query) {
    List<Post> result = new ArrayList<>(posts.values());
    result.sort(Comparator.comparingLong(Post::getId));
    return result.stream()
        .filter(post -> query.category() == null || post.getCategory() == query.category())
        .skip((long) query.page() * query.size())
        .limit(query.size())
        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
  }

  @Override
  public Optional<Post> findById(long id) {
    return Optional.ofNullable(posts.get(id));
  }

  @Override
  public boolean incrementViewCount(long id) {
    var incremented = new AtomicBoolean();
    posts.computeIfPresent(
        id,
        (key, post) -> {
          ReflectionTestUtils.setField(post, "viewCount", post.getViewCount() + 1);
          incremented.set(true);
          return post;
        });
    return incremented.get();
  }

  @Override
  public void delete(Post post) {
    posts.remove(post.getId());
  }
}
