package org.sopt.auth.domain;

import java.nio.charset.StandardCharsets;
import org.sopt.auth.domain.exception.AuthErrorCode;
import org.sopt.auth.domain.exception.AuthException;

/** 해싱 전 평문 비밀번호 정책입니다. BCrypt는 72바이트 이후를 무시하므로 더 긴 비밀번호는 거부합니다. */
public final class PasswordPolicy {
  public static final int MIN_LENGTH = 8;
  public static final int MAX_BYTES = 72;

  private PasswordPolicy() {}

  public static void validate(String rawPassword) {
    if (rawPassword == null
        || rawPassword.isBlank()
        || rawPassword.codePointCount(0, rawPassword.length()) < MIN_LENGTH
        || rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
      throw new AuthException(AuthErrorCode.INVALID_PASSWORD);
    }
  }
}
