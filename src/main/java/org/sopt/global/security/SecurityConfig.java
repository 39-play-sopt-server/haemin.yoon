package org.sopt.global.security;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.json.JsonMapper;

/** 모든 도메인에 공통으로 적용되는 인증 정책입니다. 토큰 없이 호출해야 하는 회원가입·로그인, 공개 조회, 문서, 헬스 체크만 허용하고 나머지 요청은 인증을 요구합니다. */
@Configuration
@EnableMethodSecurity
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {
  private static final String[] PUBLIC_PATHS = {
    "/error", "/actuator/health/**", "/v3/api-docs/**", "/scalar/**"
  };
  private static final String[] PUBLIC_READ_PATHS = {"/posts", "/posts/{id}"};

  @Bean
  public SecretKey jwtKey(JwtProperties properties) {
    if (properties.secret() == null || properties.secret().isBlank()) {
      throw new IllegalStateException("jwt.secret(JWT_SECRET)을 설정해야 합니다.");
    }
    if (properties.accessTokenExpiration() == null) {
      throw new IllegalStateException("jwt.access-token-expiration을 설정해야 합니다.");
    }
    // 256비트 미만의 키는 WeakKeyException으로 애플리케이션 시작 시 실패합니다.
    return Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, AccessTokenReader reader, SessionAuthority sessions, JsonMapper jsonMapper)
      throws Exception {
    var errorResponder = new SecurityErrorResponder(jsonMapper);
    return http
        // 쿠키를 사용하는 재발급은 전용 요청 헤더와 명시적인 CORS Origin으로 보호합니다.
        .csrf(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        // WebConfig의 CorsConfigurationSource로 인증 전에 preflight를 처리합니다.
        .cors(Customizer.withDefaults())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        HttpMethod.POST, "/auth/login", "/auth/signup", "/auth/refresh")
                    .permitAll()
                    .requestMatchers(PUBLIC_PATHS)
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, PUBLIC_READ_PATHS)
                    .permitAll()
                    .requestMatchers(HttpMethod.HEAD, PUBLIC_READ_PATHS)
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(errorResponder)
                    .accessDeniedHandler(errorResponder))
        .addFilterBefore(
            new JwtAuthenticationFilter(reader, sessions),
            UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
