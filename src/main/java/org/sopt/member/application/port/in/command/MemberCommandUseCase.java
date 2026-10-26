package org.sopt.member.application.port.in.command;

import org.sopt.member.domain.Member;

public interface MemberCommandUseCase {
  Member createMember(String name);

  void deleteMember(long id);
}
