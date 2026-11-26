package org.sopt.auth.adapter.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sopt.auth.adapter.in.web.dto.request.LoginRequest;
import org.sopt.auth.adapter.in.web.dto.request.SignupRequest;
import org.sopt.auth.adapter.in.web.dto.response.LoginResponse;
import org.sopt.auth.adapter.in.web.dto.response.SignupResponse;
import org.sopt.auth.application.port.in.command.AuthCommandUseCase;
import org.sopt.auth.application.port.in.command.dto.LoginResult;
import org.sopt.global.exception.BaseException;
import org.sopt.global.exception.GlobalErrorCode;
import org.sopt.global.response.BaseResponse;
import org.sopt.global.security.AccessIdentity;
import org.sopt.global.security.UserOnly;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증", description = "이메일·비밀번호 회원가입과 로그인")
@ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthController {
  private final AuthCommandUseCase auth;

  @Operation(summary = "회원가입", description = "이름·이메일·비밀번호로 가입합니다. 비밀번호는 BCrypt로 해싱해 저장합니다.")
  @ApiResponse(responseCode = "201", description = "회원가입 성공")
  @ApiResponse(responseCode = "400", ref = "#/components/responses/SignupBadRequest")
  @ApiResponse(responseCode = "409", ref = "#/components/responses/MemberEmailDuplicated")
  @PostMapping("/signup")
  @ResponseStatus(HttpStatus.CREATED)
  public BaseResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
    return BaseResponse.success(
        SignupResponse.from(auth.signup(request.name(), request.email(), request.password())),
        "회원가입이 완료되었습니다.");
  }

  @Operation(
      summary = "로그인",
      description =
          "이메일·비밀번호가 일치하면 Access Token을 발급합니다. 이후 요청에 Authorization: Bearer {accessToken}을 전달합니다.")
  @ApiResponse(responseCode = "200", description = "로그인 성공")
  @ApiResponse(responseCode = "400", ref = "#/components/responses/LoginBadRequest")
  @ApiResponse(responseCode = "401", ref = "#/components/responses/InvalidCredentials")
  @PostMapping("/login")
  public ResponseEntity<BaseResponse<LoginResponse>> login(
      @Valid @RequestBody LoginRequest request) {
    return tokenResponse(auth.login(request.email(), request.password()));
  }

  @Operation(summary = "토큰 재발급", description = "refreshToken 쿠키와 X-Refresh-Request: 1 헤더를 전송합니다.")
  @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
  @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
  @PostMapping("/refresh")
  public ResponseEntity<BaseResponse<LoginResponse>> refresh(
      @CookieValue(name = "refreshToken", required = false) String cookie,
      @RequestHeader(name = "X-Refresh-Request", required = false) String marker) {
    if (!"1".equals(marker)) throw new BaseException(GlobalErrorCode.FORBIDDEN);
    return tokenResponse(auth.refresh(cookie));
  }

  @UserOnly
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "현재 로그인 세션 종료")
  @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
  @PostMapping("/logout")
  public ResponseEntity<BaseResponse<Void>> logout(Authentication authentication) {
    auth.logout(((AccessIdentity) authentication.getDetails()).sessionId());
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookie("", 0))
        .body(BaseResponse.success(null, "로그아웃했습니다."));
  }

  private ResponseEntity<BaseResponse<LoginResponse>> tokenResponse(LoginResult tokens) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken())
        .header(
            HttpHeaders.SET_COOKIE, cookie(tokens.refreshToken(), tokens.refreshExpiresInSeconds()))
        .body(BaseResponse.success(LoginResponse.from(tokens), "토큰을 발급했습니다."));
  }

  private String cookie(String value, long maxAge) {
    return ResponseCookie.from("refreshToken", value)
        .path("/auth")
        .httpOnly(true)
        .secure(true)
        .sameSite("Strict")
        .maxAge(maxAge)
        .build()
        .toString();
  }
}
