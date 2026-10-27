package org.sopt.post.adapter.in.web;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiDocumentationTest {
  @Autowired MockMvc mvc;
  @Autowired JsonMapper json;

  @Test
  void domainResponseExampleMatchesActualErrorAndKeepsCommonDefinitions() throws Exception {
    var document =
        json.readTree(
            mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    var actual =
        json.readTree(
            mvc.perform(get("/posts/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString());
    org.junit.jupiter.api.Assertions.assertEquals(
        actual,
        document.at("/components/responses/PostNotFound/content/application~1json/example"));
    org.junit.jupiter.api.Assertions.assertEquals(
        "#/components/schemas/ErrorResponse",
        document
            .at("/components/responses/PostNotFound/content/application~1json/schema/$ref")
            .asString());
    org.junit.jupiter.api.Assertions.assertEquals(
        "VALIDATION_FAILED",
        document
            .at(
                "/components/responses/PostCreateBadRequest/content/application~1json/examples/ValidationFailed/value/code")
            .asString());
    org.junit.jupiter.api.Assertions.assertEquals(
        "INTERNAL_SERVER_ERROR",
        document
            .at("/components/responses/InternalServerError/content/application~1json/example/code")
            .asString());
  }

  @Test
  void listQueryDocumentationMatchesActualPaginationError() throws Exception {
    var document =
        json.readTree(
            mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(
                    jsonPath("$.paths['/posts'].get.parameters[*].name")
                        .value(
                            containsInAnyOrder(
                                "cursor", "size", "category", "keyword", "authorId")))
                .andReturn()
                .getResponse()
                .getContentAsString());
    var actual =
        json.readTree(
            mvc.perform(get("/posts").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString());
    org.junit.jupiter.api.Assertions.assertEquals(
        actual,
        document.at(
            "/components/responses/PostListBadRequest/content/application~1json/examples/InvalidPagination/value"));
  }

  @Test
  void exposesOnlyPostOperationsWithActualStatusesAndErrorEnvelope() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.info.title").value("PLAY SOPT SERVER Assignment API"))
        .andExpect(jsonPath("$.paths", aMapWithSize(4)))
        .andExpect(jsonPath("$.paths['/posts'].get.summary").value("게시글 목록 조회"))
        .andExpect(jsonPath("$.paths['/posts'].post.responses['201']").exists())
        .andExpect(
            jsonPath("$.paths['/posts'].post.responses['400'].$ref")
                .value("#/components/responses/PostCreateBadRequest"))
        .andExpect(
            jsonPath("$.paths['/posts/{id}'].get.responses['404'].$ref")
                .value("#/components/responses/PostNotFound"))
        .andExpect(jsonPath("$.paths['/posts/{id}'].put.responses['200']").exists())
        .andExpect(jsonPath("$.paths['/posts/{id}'].delete.responses['200']").exists())
        .andExpect(jsonPath("$.paths['/posts/{id}'].head.summary").value("게시글 응답 헤더 조회"))
        .andExpect(jsonPath("$.paths['/posts/{id}'].head.responses['200'].content").doesNotExist())
        .andExpect(
            jsonPath("$.components.responses.PostNotFound.content['application/json'].example.code")
                .value("POST_NOT_FOUND"))
        .andExpect(
            jsonPath(
                "$.components.schemas.CreatePostRequest.required",
                containsInAnyOrder("title", "content", "category", "memberId")));
  }

  @Test
  void eachOperationDocumentsOnlyItsOwnBadRequestConditions() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.paths['/posts/{id}'].get.responses['400'].$ref")
                .value("#/components/responses/InvalidPostId"))
        .andExpect(
            jsonPath("$.paths['/posts/{id}'].delete.responses['400'].$ref")
                .value("#/components/responses/InvalidPostId"))
        .andExpect(
            jsonPath("$.paths['/posts/{id}'].put.responses['400'].$ref")
                .value("#/components/responses/PostUpdateBadRequest"))
        .andExpect(
            jsonPath("$.paths['/posts'].get.responses['400'].$ref")
                .value("#/components/responses/PostListBadRequest"))
        .andExpect(
            jsonPath("$.components.responses.InvalidPostId.description", not(containsString("본문"))))
        .andExpect(
            jsonPath(
                    "$.components.responses.InvalidPostId.content['application/json'].example.code")
                .value("INVALID_REQUEST"))
        .andExpect(
            jsonPath(
                "$.components.responses.InvalidPostId.content['application/json'].example.violations",
                empty()))
        .andExpect(
            jsonPath(
                "$.components.responses.PostCreateBadRequest.content['application/json'].examples.ValidationFailed.value.violations[*].field",
                containsInAnyOrder("title", "content", "category", "memberId")))
        .andExpect(
            jsonPath(
                "$.components.responses.PostUpdateBadRequest.content['application/json'].examples.ValidationFailed.value.violations[*].field",
                containsInAnyOrder("title", "content", "version")));
  }

  @Test
  void preservesGenericResponseTypesAndNullErrorData() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.components.schemas.ErrorResponse.properties.data.type").value("null"))
        .andExpect(
            jsonPath("$.components.schemas.BaseResponsePostListResponse.properties.data.$ref")
                .value("#/components/schemas/PostListResponse"))
        .andExpect(
            jsonPath("$.components.schemas.BaseResponsePostResponse.properties.data.$ref")
                .value("#/components/schemas/PostResponse"));
  }

  @Test
  void documentsPostLengthLimits() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.components.schemas.CreatePostRequest.properties.title.maxLength")
                .value(200))
        .andExpect(
            jsonPath("$.components.schemas.CreatePostRequest.properties.content.maxLength")
                .value(10000))
        .andExpect(
            jsonPath("$.components.schemas.CreateMemberRequest.properties.name.maxLength")
                .value(50))
        .andExpect(
            jsonPath("$.components.schemas.UpdatePostRequest.properties.title.maxLength")
                .value(200))
        .andExpect(
            jsonPath("$.components.schemas.UpdatePostRequest.properties.content.maxLength")
                .value(10000));
  }

  @Test
  void validationViolationRequiresAllPropertiesAndAllowsNullField() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.components.schemas.ErrorResponse.properties.violations.items.required",
                containsInAnyOrder("field", "code", "message")))
        .andExpect(
            jsonPath(
                "$.components.schemas.ErrorResponse.properties.violations.items.properties.field.type",
                containsInAnyOrder("string", "null")))
        .andExpect(
            jsonPath(
                    "$.components.schemas.ErrorResponse.properties.violations.items.properties.code.type")
                .value("string"))
        .andExpect(
            jsonPath(
                    "$.components.schemas.ErrorResponse.properties.violations.items.properties.message.type")
                .value("string"));
  }

  @Test
  void documentsRequiredVersionAndEditConflict() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.components.schemas.UpdatePostRequest.required",
                containsInAnyOrder("title", "content", "version")))
        .andExpect(
            jsonPath("$.components.schemas.UpdatePostRequest.properties.version.minimum").value(0))
        .andExpect(
            jsonPath("$.components.schemas.PostResponse.properties.version.type").value("integer"))
        .andExpect(
            jsonPath("$.paths['/posts/{id}'].put.responses['409'].$ref")
                .value("#/components/responses/PostEditConflict"))
        .andExpect(
            jsonPath("$.paths['/posts/{id}'].delete.responses['409'].$ref")
                .value("#/components/responses/PostEditConflict"))
        .andExpect(
            jsonPath(
                    "$.components.responses.PostEditConflict.content['application/json'].example.code")
                .value("CONFLICT"));
  }

  @Test
  void servesScalarPointingAtGeneratedSpecification() throws Exception {
    mvc.perform(get("/scalar"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(content().string(containsString("/v3/api-docs")));
  }
}
