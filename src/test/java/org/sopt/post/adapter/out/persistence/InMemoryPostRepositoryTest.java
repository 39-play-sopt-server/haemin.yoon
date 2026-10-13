package org.sopt.post.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sopt.post.domain.Category;
import org.sopt.post.domain.Post;

class InMemoryPostRepositoryTest {
  private final InMemoryPostRepository repository = new InMemoryPostRepository();

  private Post post(String title) {
    return new Post(title, "본문", Category.GENERAL, "작성자");
  }

  @Test
  void returnedListCannotChangeStoredMembership() {
    repository.save(post("제목"));
    repository.findAll().clear();
    assertEquals(1, repository.findAll().size());
    assertEquals("제목", repository.findById(1).orElseThrow().getTitle());
  }

  @Test
  void listsByIdAndDoesNotRenumberAfterDeletion() {
    repository.save(post("첫 글"));
    var second = post("둘째 글");
    repository.save(second);
    repository.save(post("셋째 글"));
    assertEquals(List.of(1L, 2L, 3L), repository.findAll().stream().map(Post::getId).toList());
    repository.delete(second);
    assertTrue(repository.findById(2).isEmpty());
    assertEquals(List.of(1L, 3L), repository.findAll().stream().map(Post::getId).toList());
    assertEquals("셋째 글", repository.findById(3).orElseThrow().getTitle());
  }

  @Test
  void savingPersistedEntityThroughCreationPortIsRejected() {
    var existing = post("기존 글");
    repository.save(existing);
    assertThrows(IllegalArgumentException.class, () -> repository.save(existing));
    assertEquals(1, repository.findAll().size());
    assertEquals("기존 글", repository.findById(existing.getId()).orElseThrow().getTitle());
  }
}
