package org.sopt.member;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sopt.support.MemberDatabaseSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberHttpApiTest extends MemberDatabaseSupport {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired JsonMapper json;

  @Test
  void createsMemberLinksPostAndKeepsAuthorAfterSoftDeletion() throws Exception {
    var response =
        mvc.perform(
                post("/members")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"홍길동\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.name").value("홍길동"))
            .andReturn()
            .getResponse();
    long id = json.readTree(response.getContentAsString()).get("data").get("id").longValue();
    assertNotNull(
        jdbc.queryForObject(
            "SELECT created_at FROM members WHERE id = ?", java.time.LocalDateTime.class, id));
    mvc.perform(
            post("/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\"제목\",\"content\":\"본문\",\"category\":\"GENERAL\",\"memberId\":%d}"
                        .formatted(id)))
        .andExpect(status().isCreated());
    long postId = jdbc.queryForObject("SELECT id FROM posts", Long.class);
    assertEquals(id, jdbc.queryForObject("SELECT member_id FROM posts", Long.class));
    mvc.perform(get("/posts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].author").value("홍길동"));
    mvc.perform(get("/posts/{id}", postId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.author").value("홍길동"));
    mvc.perform(delete("/members/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());
    assertTrue(jdbc.queryForObject("SELECT deleted FROM members WHERE id = ?", Boolean.class, id));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM posts", Integer.class));
    mvc.perform(get("/posts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].author").value("홍길동"));
    mvc.perform(get("/posts/{id}", postId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.author").value("홍길동"));
    mvc.perform(
            post("/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\"제목\",\"content\":\"본문\",\"category\":\"GENERAL\",\"memberId\":%d}"
                        .formatted(id)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    mvc.perform(delete("/members/{id}", id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM posts", Integer.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"name\":null}", "{\"name\":\" \"}"})
  void rejectsMissingMemberName(String body) throws Exception {
    mvc.perform(post("/members").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.violations[0].field").value("name"));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM members", Integer.class));
  }

  @Test
  void validatesNameBoundariesAndNulWithoutWritingInvalidMembers() throws Exception {
    mvc.perform(
            post("/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("name", "가".repeat(51)))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.violations[0].code").value("INVALID_LENGTH"));
    mvc.perform(
            post("/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("name", "앞\0뒤"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MEMBER_NAME_NUL_NOT_ALLOWED"));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM members", Integer.class));
    String name = "😀".repeat(25);
    mvc.perform(
            post("/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("name", name))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.name").value(name));
  }

  @ParameterizedTest
  @ValueSource(strings = {"null", "0", "-1", "1.5"})
  void rejectsInvalidMemberIdWithoutWritingPost(String id) throws Exception {
    mvc.perform(
            post("/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\"제목\",\"content\":\"본문\",\"category\":\"GENERAL\",\"memberId\":%s}"
                        .formatted(id)))
        .andExpect(status().isBadRequest());
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM posts", Integer.class));
  }

  @Test
  void missingMembersReturn404AndLegacyAuthorCannotCreatePost() throws Exception {
    mvc.perform(delete("/members/99999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    mvc.perform(
            post("/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\"제목\",\"content\":\"본문\",\"category\":\"GENERAL\",\"memberId\":99999}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    mvc.perform(
            post("/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\"제목\",\"content\":\"본문\",\"category\":\"GENERAL\",\"author\":\"임의 이름\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.violations[0].field").value("memberId"));
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM posts", Integer.class));
  }

  @Test
  void allowsSameNameForDifferentMembers() throws Exception {
    var first = memberCommands.createMember("동명이인");
    var second = memberCommands.createMember("동명이인");
    assertNotEquals(first.getId(), second.getId());
  }

  @Test
  void documentsMemberEndpointsAndPostMemberReference() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/members'].post.responses['201']").exists())
        .andExpect(
            jsonPath("$.paths['/members/{id}'].delete.responses['404'].$ref")
                .value("#/components/responses/MemberNotFound"))
        .andExpect(
            jsonPath("$.paths['/posts'].post.responses['404'].$ref")
                .value("#/components/responses/MemberNotFound"))
        .andExpect(jsonPath("$.components.schemas.CreatePostRequest.properties.memberId").exists())
        .andExpect(
            jsonPath("$.components.schemas.CreatePostRequest.properties.author").doesNotExist());
  }
}
