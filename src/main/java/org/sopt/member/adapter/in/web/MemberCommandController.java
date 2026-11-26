package org.sopt.member.adapter.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.sopt.global.response.BaseResponse;
import org.sopt.global.security.SelfOrAdmin;
import org.sopt.member.application.port.in.command.MemberCommandUseCase;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 회원 등록은 인증 도메인의 회원가입 API가 담당합니다. */
@Tag(name = "회원", description = "회원 조회·삭제")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
@ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/members", produces = MediaType.APPLICATION_JSON_VALUE)
public class MemberCommandController {
  private final MemberCommandUseCase commands;

  @Operation(
      summary = "회원 삭제",
      description = "회원을 소프트 삭제합니다. 기존 게시글과 작성자 이름은 유지하며 삭제된 회원은 새 게시글을 작성할 수 없습니다.")
  @ApiResponse(responseCode = "200", description = "회원 삭제 성공")
  @ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidMemberId")
  @ApiResponse(responseCode = "404", ref = "#/components/responses/MemberNotFound")
  @SelfOrAdmin
  @DeleteMapping("/{id}")
  public BaseResponse<Void> deleteMember(@PathVariable("id") long memberId) {
    commands.deleteMember(memberId);
    return BaseResponse.success(null, "회원이 삭제되었습니다.");
  }
}
