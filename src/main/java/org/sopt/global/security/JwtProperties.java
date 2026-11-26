package org.sopt.global.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 서명 키와 Access Token 유효 시간입니다. secret은 Base64로 인코딩한 32바이트 이상의 값이며 환경 변수로만 주입합니다.
 *
 * @param secret Base64 인코딩된 HMAC-SHA 서명 키
 * @param accessTokenExpiration Access Token 유효 시간. 단위가 없으면 밀리초입니다.
 */
@ConfigurationProperties("jwt")
public record JwtProperties(String secret, Duration accessTokenExpiration) {}
