package org.sopt.member.adapter.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.sopt.global.response.BaseResponse;
import org.sopt.global.security.AdminOnly;
import org.sopt.global.security.UserOnly;
import org.sopt.member.adapter.in.web.dto.response.MemberResponse;
import org.sopt.member.application.port.in.query.MemberQueryUseCase;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "회원", description = "회원 조회·삭제")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
@ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/members", produces = MediaType.APPLICATION_JSON_VALUE)
public class MemberQueryController {
  private final MemberQueryUseCase queries;

  @Operation(summary = "내 정보 조회", description = "Access Token의 회원을 조회합니다. 탈퇴한 회원의 토큰은 401로 거부됩니다.")
  @ApiResponse(responseCode = "200", description = "내 정보 조회 성공")
  @ApiResponse(responseCode = "404", ref = "#/components/responses/MemberNotFound")
  @UserOnly
  @GetMapping("/me")
  public BaseResponse<MemberResponse> me(@AuthenticationPrincipal Long memberId) {
    return BaseResponse.success(
        MemberResponse.from(queries.getActiveMember(memberId)), "내 정보를 조회했습니다.");
  }

  @Operation(summary = "전체 활성 회원 목록 조회 (관리자)")
  @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
  @AdminOnly
  @GetMapping
  public BaseResponse<List<MemberResponse>> members() {
    return BaseResponse.success(
        queries.listActive().stream().map(MemberResponse::from).toList(), "회원 목록을 조회했습니다.");
  }
}
