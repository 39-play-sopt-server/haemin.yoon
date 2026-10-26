package org.sopt.member.adapter.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sopt.global.response.BaseResponse;
import org.sopt.member.adapter.in.web.dto.request.CreateMemberRequest;
import org.sopt.member.adapter.in.web.dto.response.MemberResponse;
import org.sopt.member.application.port.in.command.MemberCommandUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "회원", description = "회원 생성·삭제")
@ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/members", produces = MediaType.APPLICATION_JSON_VALUE)
public class MemberCommandController {
  private final MemberCommandUseCase commands;

  @Operation(summary = "회원 생성", description = "회원 이름으로 생성하며 응답의 ID를 게시글 작성에 사용합니다.")
  @ApiResponse(responseCode = "201", description = "회원 생성 성공")
  @ApiResponse(responseCode = "400", ref = "#/components/responses/MemberCreateBadRequest")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public BaseResponse<MemberResponse> createMember(
      @Valid @RequestBody CreateMemberRequest request) {
    return BaseResponse.success(
        MemberResponse.from(commands.createMember(request.name())), "회원이 생성되었습니다.");
  }

  @Operation(
      summary = "회원 삭제",
      description = "회원을 소프트 삭제합니다. 기존 게시글과 작성자 이름은 유지하며 삭제된 회원은 새 게시글을 작성할 수 없습니다.")
  @ApiResponse(responseCode = "200", description = "회원 삭제 성공")
  @ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidMemberId")
  @ApiResponse(responseCode = "404", ref = "#/components/responses/MemberNotFound")
  @DeleteMapping("/{id}")
  public BaseResponse<Void> deleteMember(@PathVariable("id") long id) {
    commands.deleteMember(id);
    return BaseResponse.success(null, "회원이 삭제되었습니다.");
  }
}
