package org.sopt.global.config;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 모든 웹 어댑터에 공통으로 적용되는 브라우저 접근 정책을 설정합니다. */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class WebConfig implements WebMvcConfigurer {

  private static final List<String> ALLOWED_METHODS =
      List.of(
          HttpMethod.GET.name(),
          HttpMethod.HEAD.name(),
          HttpMethod.POST.name(),
          HttpMethod.PUT.name(),
          HttpMethod.PATCH.name(),
          HttpMethod.DELETE.name(),
          HttpMethod.OPTIONS.name());

  private final CorsProperties corsProperties;

  public WebConfig(CorsProperties corsProperties) {
    this.corsProperties = corsProperties;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    if (corsProperties.allowedOrigins().isEmpty()) {
      return;
    }

    registry
        .addMapping("/**")
        .allowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
        .allowedMethods(ALLOWED_METHODS.toArray(String[]::new))
        .allowedHeaders(HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT, HttpHeaders.IF_MATCH)
        .exposedHeaders(HttpHeaders.LOCATION, HttpHeaders.ETAG)
        .allowCredentials(false)
        .maxAge(3600);
  }
}
