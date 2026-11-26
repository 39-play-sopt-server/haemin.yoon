package org.sopt.auth.application.port.in.command.dto;

public record LoginResult(
    String accessToken, long expiresInSeconds, String refreshToken, long refreshExpiresInSeconds) {
  @Override
  public String toString() {
    return "LoginResult[토큰 비공개]";
  }
}
