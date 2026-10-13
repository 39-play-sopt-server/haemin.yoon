package org.sopt.global.exception;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.servlet.Filter;
import jakarta.servlet.ServletException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sopt.global.response.BaseResponse;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/** MockMvc로는 수행되지 않는 실제 서블릿 ERROR 디스패치까지 검증합니다. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:h2:mem:error-http;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Import({
  ErrorHttpIntegrationTest.ErrorFixtures.class,
  ErrorHttpIntegrationTest.ProbeController.class
})
class ErrorHttpIntegrationTest {
  @LocalServerPort int port;

  @ParameterizedTest
  @CsvSource({
    "filter-failure,500,INTERNAL_SERVER_ERROR",
    "send-error,503,SERVICE_UNAVAILABLE",
    "checked,500,INTERNAL_SERVER_ERROR",
    "parameter,400,INVALID_REQUEST",
    "rate-limit,429,TOO_MANY_REQUESTS"
  })
  void preservesStatusAndSafeEnvelopeOverRealHttp(String path, int status, String code)
      throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      var request =
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/probe/" + path))
              .timeout(Duration.ofSeconds(10))
              .header("Accept", "text/html")
              .build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      assertEquals(status, response.statusCode());
      assertTrue(
          response
              .headers()
              .firstValue("Content-Type")
              .orElseThrow()
              .startsWith("application/json"));
      var json = JsonMapper.builder().build().readTree(response.body());
      assertEquals(5, json.size());
      assertTrue(json.get("violations").isArray());
      assertEquals(0, json.get("violations").size());
      assertFalse(json.get("success").booleanValue());
      assertEquals(code, json.get("code").stringValue());
      assertFalse(json.get("message").stringValue().isBlank());
      assertTrue(json.has("data"));
      assertTrue(json.get("data").isNull());
      assertFalse(response.body().contains("secret"));
      if (status == 429) {
        assertEquals("30", response.headers().firstValue("Retry-After").orElseThrow());
      }
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ErrorFixtures {
    @Bean
    FilterRegistrationBean<Filter> failingFilter() {
      Filter filter =
          (request, response, chain) -> {
            var httpRequest = (jakarta.servlet.http.HttpServletRequest) request;
            if (httpRequest.getRequestURI().endsWith("filter-failure")) {
              throw new ServletException("secret filter details");
            }
            ((jakarta.servlet.http.HttpServletResponse) response)
                .sendError(503, "secret server details");
          };
      var registration = new FilterRegistrationBean<>(filter);
      registration.addUrlPatterns("/probe/filter-failure", "/probe/send-error");
      return registration;
    }
  }

  @RestController
  static class ProbeController {
    @GetMapping("/probe/checked")
    BaseResponse<Void> checked() throws Exception {
      throw new Exception("secret checked exception");
    }

    @GetMapping("/probe/parameter")
    BaseResponse<String> parameter(@RequestParam("required") String value) {
      return BaseResponse.success(value, "조회 성공");
    }

    @GetMapping("/probe/rate-limit")
    BaseResponse<Void> rateLimit() {
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "secret reason") {
        @Override
        public org.springframework.http.HttpHeaders getHeaders() {
          var headers = new org.springframework.http.HttpHeaders();
          headers.set("Retry-After", "30");
          return headers;
        }
      };
    }
  }
}
