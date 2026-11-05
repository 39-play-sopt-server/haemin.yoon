package org.sopt.global.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 브라우저에서 API 호출을 허용할 신뢰 Origin 목록입니다. */
@ConfigurationProperties("app.cors")
public record CorsProperties(List<String> allowedOrigins) {

  public CorsProperties {
    allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
  }
}
