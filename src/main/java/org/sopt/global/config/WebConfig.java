package org.sopt.global.config;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 모든 웹 어댑터에 공통으로 적용되는 브라우저 접근 정책을 설정합니다. 보안 필터가 인증 전에 preflight를 처리하도록 MVC 매핑 대신
 * CorsConfigurationSource로 등록합니다.
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class WebConfig {

  private static final List<String> ALLOWED_METHODS =
      List.of(
          HttpMethod.GET.name(),
          HttpMethod.HEAD.name(),
          HttpMethod.POST.name(),
          HttpMethod.PUT.name(),
          HttpMethod.PATCH.name(),
          HttpMethod.DELETE.name(),
          HttpMethod.OPTIONS.name());

  @Bean
  public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
    var source = new UrlBasedCorsConfigurationSource();
    if (corsProperties.allowedOrigins().isEmpty()) {
      return source;
    }

    var configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(corsProperties.allowedOrigins());
    configuration.setAllowedMethods(ALLOWED_METHODS);
    configuration.setAllowedHeaders(
        List.of(
            HttpHeaders.CONTENT_TYPE,
            HttpHeaders.ACCEPT,
            HttpHeaders.IF_MATCH,
            HttpHeaders.AUTHORIZATION,
            "X-Refresh-Request"));
    configuration.setExposedHeaders(
        List.of(HttpHeaders.LOCATION, HttpHeaders.ETAG, HttpHeaders.AUTHORIZATION));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
