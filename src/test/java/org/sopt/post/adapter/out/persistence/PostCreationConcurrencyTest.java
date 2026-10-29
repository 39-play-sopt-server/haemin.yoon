package org.sopt.post.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sopt.support.MemberDatabaseSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostCreationConcurrencyTest extends MemberDatabaseSupport {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void clearPosts() {
    jdbc.update("DELETE FROM posts");
  }

  @Test
  void concurrentHttpCreatesPersistEveryPostWithUniqueDatabaseIds() throws Exception {
    var barrier = new CyclicBarrier(8);
    try (var executor = Executors.newFixedThreadPool(8)) {
      var futures = new ArrayList<Future<?>>();
      for (int worker = 0; worker < 8; worker++) {
        int first = worker * 5;
        futures.add(
            executor.submit(
                () -> {
                  barrier.await(10, TimeUnit.SECONDS);
                  for (int i = first; i < first + 5; i++) {
                    mvc.perform(
                            post("/posts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                    """
                                    {"title":"post-%d","content":"본문","category":"GENERAL","memberId":1}
                                    """
                                        .formatted(i)))
                        .andExpect(status().isCreated());
                  }
                  return null;
                }));
      }
      for (var future : futures) {
        future.get(20, TimeUnit.SECONDS);
      }
    }
    assertEquals(40, jdbc.queryForObject("SELECT COUNT(*) FROM posts", Integer.class));
    assertEquals(40, jdbc.queryForObject("SELECT COUNT(DISTINCT id) FROM posts", Integer.class));
    assertEquals(
        IntStream.range(0, 40).mapToObj(i -> "post-" + i).sorted().toList(),
        jdbc.queryForList("SELECT title FROM posts ORDER BY title", String.class));
    assertEquals(0L, jdbc.queryForObject("SELECT SUM(view_count) FROM posts", Long.class));
  }
}
