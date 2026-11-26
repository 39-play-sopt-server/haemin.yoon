package org.sopt.auth.application.port.in.command;

import java.util.UUID;
import org.sopt.auth.application.port.in.command.dto.LoginResult;
import org.sopt.auth.application.port.in.command.dto.SignupResult;

public interface AuthCommandUseCase {
  /** 비밀번호 정책을 검증하고 해싱한 뒤 회원을 등록합니다. */
  SignupResult signup(String name, String email, String rawPassword);

  /** 활성 회원의 이메일·비밀번호가 일치하면 Access Token을 발급합니다. */
  LoginResult login(String email, String rawPassword);

  LoginResult refresh(String token);

  void logout(UUID sessionId);
}
