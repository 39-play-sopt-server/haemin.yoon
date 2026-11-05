package org.sopt.post.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.sopt.post.application.port.in.query.dto.PostInfo;
import org.sopt.post.application.port.in.query.dto.PostListQuery;
import org.sopt.post.domain.Category;
import org.sopt.post.domain.Post;

class ConcurrentPostStorageTest {
  @Test
  void concurrentWritesPreserveAllPostsAndSortedListing() throws Exception {
    var repository = new InMemoryPostRepository();
    runConcurrently(
        worker -> {
          for (int i = 1; i <= 1000; i++) {
            repository.save(new Post("제목", "본문", Category.GENERAL, member("작성자")));
          }
        });
    var posts = new ArrayList<PostInfo>();
    var queries = new org.sopt.post.application.service.query.PostQueryService(repository);
    org.sopt.post.application.port.in.query.dto.PostCursor cursor = null;
    do {
      var result = queries.getPosts(new PostListQuery(cursor, 100, null, null, null));
      posts.addAll(result.posts());
      cursor = result.nextCursor();
    } while (cursor != null);
    assertEquals(8000, posts.size());
    assertEquals(
        java.util.stream.LongStream.rangeClosed(1, 8000).boxed().toList(),
        posts.stream().map(PostInfo::id).sorted().toList());
    for (int i = 1; i < posts.size(); i++) {
      PostInfo previous = posts.get(i - 1);
      PostInfo current = posts.get(i);
      assertFalse(previous.createdAt().isBefore(current.createdAt()));
      if (previous.createdAt().equals(current.createdAt())) {
        assertTrue(previous.id() > current.id());
      }
    }
  }

  private void runConcurrently(java.util.function.IntConsumer action) throws Exception {
    var barrier = new CyclicBarrier(8);
    try (var executor = Executors.newFixedThreadPool(8)) {
      List<Future<?>> futures = new ArrayList<>();
      for (int worker = 0; worker < 8; worker++) {
        int index = worker;
        futures.add(
            executor.submit(
                () -> {
                  barrier.await();
                  action.accept(index);
                  return null;
                }));
      }
      for (var future : futures) {
        future.get(15, java.util.concurrent.TimeUnit.SECONDS);
      }
    }
  }

  private static org.sopt.post.domain.PostAuthor member(String name) {
    return new org.sopt.post.domain.PostAuthor(1L, name);
  }
}
