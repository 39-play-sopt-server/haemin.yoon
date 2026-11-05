package org.sopt.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = "app.cors.allowed-origins=https://frontend.example.com,https://admin.example.com")
class CorsIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void allowsPreflightFromConfiguredOrigin() throws Exception {
    mockMvc
        .perform(
            options("/posts")
                .header(HttpHeaders.ORIGIN, "https://frontend.example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, HttpHeaders.CONTENT_TYPE))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://frontend.example.com"))
        .andExpect(
            header()
                .string(
                    HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                    "GET,HEAD,POST,PUT,PATCH,DELETE,OPTIONS"));
  }

  @Test
  void rejectsPreflightFromUnknownOrigin() throws Exception {
    mockMvc
        .perform(
            options("/posts")
                .header(HttpHeaders.ORIGIN, "https://attacker.example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name()))
        .andExpect(status().isForbidden());
  }

  @Test
  void allowsEachOriginInCommaSeparatedConfiguration() throws Exception {
    mockMvc
        .perform(
            options("/members")
                .header(HttpHeaders.ORIGIN, "https://admin.example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name()))
        .andExpect(status().isOk())
        .andExpect(
            header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://admin.example.com"));
  }
}
