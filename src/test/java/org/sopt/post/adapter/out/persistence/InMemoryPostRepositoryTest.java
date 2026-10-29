package org.sopt.post.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sopt.post.application.port.out.dto.PostListCriteria;
import org.sopt.post.domain.Category;
import org.sopt.post.domain.Post;

class InMemoryPostRepositoryTest {
  private final InMemoryPostRepository repository = new InMemoryPostRepository();

  private Post post(String title) {
    return new Post(title, "본문", Category.GENERAL, member("작성자"));
  }

  @Test
  void returnedListCannotChangeStoredMembership() {
    repository.save(post("제목"));
    repository.findAll(criteria()).clear();
    assertEquals(1, repository.findAll(criteria()).size());
    assertEquals("제목", repository.findById(1).orElseThrow().getTitle());
  }

  @Test
  void listsLatestFirstAndDoesNotRenumberAfterDeletion() {
    repository.save(post("첫 글"));
    var second = post("둘째 글");
    repository.save(second);
    repository.save(post("셋째 글"));
    assertEquals(
        List.of(3L, 2L, 1L), repository.findAll(criteria()).stream().map(Post::getId).toList());
    repository.delete(second);
    assertTrue(repository.findById(2).isEmpty());
    assertEquals(
        List.of(3L, 1L), repository.findAll(criteria()).stream().map(Post::getId).toList());
    assertEquals("셋째 글", repository.findById(3).orElseThrow().getTitle());
  }

  @Test
  void savingPersistedEntityThroughCreationPortIsRejected() {
    var existing = post("기존 글");
    repository.save(existing);
    assertThrows(IllegalArgumentException.class, () -> repository.save(existing));
    assertEquals(1, repository.findAll(criteria()).size());
    assertEquals("기존 글", repository.findById(existing.getId()).orElseThrow().getTitle());
  }

  private static PostListCriteria criteria() {
    return new PostListCriteria(null, null, 100, null, null, null);
  }

  private static org.sopt.post.domain.PostAuthor member(String name) {
    return new org.sopt.post.domain.PostAuthor(1L, name);
  }
}
