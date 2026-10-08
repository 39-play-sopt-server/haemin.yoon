package org.sopt.post.adapter.out.persistence;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sopt.post.domain.Category;
import org.sopt.post.domain.Post;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryPostRepositoryTest {
  private final InMemoryPostRepository repository = new InMemoryPostRepository();

  private Post post(long id, String title) {
    return new Post(id, title, "본문", Category.GENERAL, "작성자");
  }

  @Test
  void returnedListCannotChangeStoredMembership() {
    repository.save(post(1, "제목"));
    repository.findAll().clear();
    assertEquals(1, repository.findAll().size());
    assertEquals("제목", repository.findById(1).orElseThrow().getTitle());
  }

  @Test
  void updateReplacesOnlySameIdAndDoesNotInsertMissingPost() {
    repository.save(post(1, "첫 글"));
    repository.save(post(3, "셋째 글"));
    assertTrue(repository.update(post(3, "수정 제목")));
    assertFalse(repository.update(post(2, "없는 글")));
    assertEquals("첫 글", repository.findById(1).orElseThrow().getTitle());
    assertEquals("수정 제목", repository.findById(3).orElseThrow().getTitle());
    assertTrue(repository.findById(2).isEmpty());
  }

  @Test
  void listsByIdAndDoesNotRenumberAfterDeletion() {
    repository.save(post(30, "셋째 글"));
    repository.save(post(10, "첫 글"));
    repository.save(post(20, "둘째 글"));
    assertEquals(List.of(10L, 20L, 30L), repository.findAll().stream().map(Post::getId).toList());
    assertTrue(repository.deleteById(20));
    assertFalse(repository.deleteById(20));
    assertEquals(List.of(10L, 30L), repository.findAll().stream().map(Post::getId).toList());
    assertEquals("셋째 글", repository.findById(30).orElseThrow().getTitle());
  }

  @Test
  void duplicateSaveDoesNotOverwriteExistingPost() {
    repository.save(post(1, "기존 글"));
    assertThrows(IllegalStateException.class, () -> repository.save(post(1, "중복 글")));
    assertEquals("기존 글", repository.findById(1).orElseThrow().getTitle());
  }
}
