package org.sopt.post.adapter.in.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sopt.global.exception.GlobalExceptionHandler;
import org.sopt.post.adapter.in.web.dto.PostCategory;
import org.sopt.post.adapter.in.web.dto.request.CreatePostRequest;
import org.sopt.post.adapter.out.persistence.InMemoryPostRepository;
import org.sopt.post.application.facade.PostFacade;
import org.sopt.post.application.port.in.query.PostListQuery;
import org.sopt.post.application.service.command.PostCommandService;
import org.sopt.post.application.service.query.PostQueryService;
import org.sopt.post.domain.Post;
import org.sopt.support.MemberUnitSupport;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PostCommandQueryControllerTest extends MemberUnitSupport {
  private final InMemoryPostRepository repository = new InMemoryPostRepository();
  private final PostCommandController commands =
      new PostCommandController(new PostCommandService(repository, repository, memberQueries));
  private final PostQueryController queries =
      new PostQueryController(
          new PostQueryService(repository),
          new PostFacade(
              new PostCommandService(repository, repository, memberQueries),
              new PostQueryService(repository)));
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(commands, queries)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  static Stream<Arguments> invalidRequests() {
    return Stream.of(
        Arguments.of("", "INVALID_REQUEST"),
        Arguments.of(
            "{\"title\":\"\",\"content\":\"본문\",\"category\":\"GENERAL\",\"memberId\":1}",
            "VALIDATION_FAILED"),
        Arguments.of(
            "{\"title\":\"제목\",\"content\":\"\",\"category\":\"GENERAL\",\"memberId\":1}",
            "VALIDATION_FAILED"),
        Arguments.of("{\"title\":\"제목\",\"content\":\"본문\",\"memberId\":1}", "VALIDATION_FAILED"),
        Arguments.of(
            "{\"title\":\"제목\",\"content\":\"본문\",\"category\":\"GENERAL\",\"memberId\":null}",
            "VALIDATION_FAILED"));
  }

  @ParameterizedTest
  @MethodSource("invalidRequests")
  void creationErrorsBecomeConsistentResponses(String request, String code) throws Exception {
    mvc.perform(post("/posts").contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value(code))
        .andExpect(jsonPath("$.data").isEmpty());
    assertTrue(queries.getPosts(0, 100, null).data().isEmpty());
  }

  @Test
  void missingUpdateBodyReturnsInvalidRequestAndPreservesPost() throws Exception {
    createPost();
    mvc.perform(put("/posts/1").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    assertEquals("제목", queries.getPost(1).getBody().data().title());
  }

  @Test
  void invalidUpdatePreservesBothFields() throws Exception {
    createPost();
    mvc.perform(
            put("/posts/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"새 제목\",\"content\":\" \",\"version\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    assertEquals("제목", queries.getPost(1).getBody().data().title());
    assertEquals("본문", queries.getPost(1).getBody().data().content());
  }

  @Test
  void responseSnapshotsRemainUnchangedAfterHttpMutations() throws Exception {
    createPost();
    var original = queries.getPost(1).getBody().data();
    var list = queries.getPosts(0, 100, null).data();
    assertThrows(UnsupportedOperationException.class, list::clear);
    mvc.perform(
            put("/posts/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"수정 제목\",\"content\":\"수정 본문\",\"version\":0}"))
        .andExpect(status().isOk());
    assertEquals("제목", original.title());
    assertEquals("본문", list.getFirst().content());
    assertEquals(original.createdAt(), queries.getPost(1).getBody().data().createdAt());
    mvc.perform(delete("/posts/1")).andExpect(status().isOk());
    assertEquals(1, list.size());
    mvc.perform(get("/posts/1")).andExpect(status().isNotFound());
  }

  @Test
  void unexpectedServiceFailureUsesMvcAdviceAndHidesInternalDetails() throws Exception {
    var failingRepository = new InMemoryPostRepository();
    var service =
        new PostQueryService(failingRepository) {
          @Override
          public List<Post> getPosts(PostListQuery query) {
            throw new IllegalStateException("internal secret details");
          }
        };
    var failingMvc =
        MockMvcBuilders.standaloneSetup(
                new PostQueryController(
                    service,
                    new PostFacade(
                        new PostCommandService(failingRepository, failingRepository, memberQueries),
                        service)))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    failingMvc
        .perform(get("/posts"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
        .andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다."))
        .andExpect(jsonPath("$.data").isEmpty());
  }

  private void createPost() {
    commands.createPost(new CreatePostRequest("제목", "본문", PostCategory.QUESTION, memberId("작성자")));
  }
}
