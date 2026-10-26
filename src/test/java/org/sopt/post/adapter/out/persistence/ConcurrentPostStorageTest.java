package org.sopt.post.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.sopt.post.application.port.in.query.PostListQuery;
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
    var posts = new ArrayList<Post>();
    for (int page = 0; page < 80; page++) {
      posts.addAll(repository.findAll(new PostListQuery(page, 100, null)));
    }
    assertEquals(8000, posts.size());
    for (int i = 0; i < posts.size(); i++) {
      assertEquals(i + 1L, posts.get(i).getId());
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

  private static org.sopt.member.domain.Member member(String name) {
    return new org.sopt.member.domain.Member(name);
  }
}
