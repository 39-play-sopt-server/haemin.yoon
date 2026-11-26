package org.sopt.member.application.port.in.command;

import org.sopt.member.application.port.in.dto.MemberInfo;

public interface MemberCommandUseCase {
  /** 해시된 비밀번호로 회원을 등록합니다. 이메일은 정규화 후 탈퇴 회원을 포함해 중복을 허용하지 않습니다. */
  MemberInfo registerMember(String name, String email, String passwordHash);

  void deleteMember(long id);
}
