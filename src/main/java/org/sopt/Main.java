package org.sopt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * Spring Boot 애플리케이션을 시작하고 org.sopt 하위 컴포넌트를 등록합니다. 인증은 회원 정보와 JWT로 처리하므로 Spring Security의 임시 user
 * 계정은 만들지 않습니다.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class Main {

  public static void main(String[] args) {
    SpringApplication.run(Main.class, args);
  }
}
