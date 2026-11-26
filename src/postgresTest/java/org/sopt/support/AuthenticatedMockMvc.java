package org.sopt.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

/** 인증 이후의 도메인 계약 테스트에 사용하는 관리자 principal입니다. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AutoConfigureMockMvc
@Import(AuthenticatedMockMvc.Configuration.class)
public @interface AuthenticatedMockMvc {
  long DEFAULT_MEMBER_ID = 1L;

  @TestConfiguration(proxyBeanMethods = false)
  class Configuration {
    @Bean
    MockMvcBuilderCustomizer authenticatedRequests() {
      var auth =
          UsernamePasswordAuthenticationToken.authenticated(
              DEFAULT_MEMBER_ID, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
      return builder ->
          builder.defaultRequest(
              get("/").with(SecurityMockMvcRequestPostProcessors.authentication(auth)));
    }
  }
}
