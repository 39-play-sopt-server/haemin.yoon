package org.sopt.member.application.port.in.command;

import org.sopt.member.application.port.in.dto.MemberInfo;

public interface MemberCommandUseCase {
  MemberInfo createMember(String name);

  void deleteMember(long id);
}
