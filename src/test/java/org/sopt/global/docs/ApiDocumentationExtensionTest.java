package org.sopt.global.docs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiDocumentationExtensionTest.AnotherDomainController.class)
class ApiDocumentationExtensionTest {
  @Autowired MockMvc mvc;

  @Test
  void newDomainIsDocumentedWithoutChangingScanSettingsAndErrorHandlerStaysHidden()
      throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/documentation-extension-probe'].get").exists())
        .andExpect(jsonPath("$.paths['/posts'].get").exists())
        .andExpect(jsonPath("$.paths['/error']").doesNotExist());
  }

  // 게시글 패키지 밖의 새 컨트롤러를 이 테스트에서만 등록합니다.
  @TestComponent
  @RestController
  static class AnotherDomainController {
    @GetMapping("/documentation-extension-probe")
    public String probe() {
      return "ok";
    }
  }
}
