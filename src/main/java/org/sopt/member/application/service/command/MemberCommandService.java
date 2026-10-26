package org.sopt.member.application.service.command;

import lombok.RequiredArgsConstructor;
import org.sopt.member.application.port.in.command.MemberCommandUseCase;
import org.sopt.member.application.port.out.LoadMemberPort;
import org.sopt.member.application.port.out.SaveMemberPort;
import org.sopt.member.domain.Member;
import org.sopt.member.domain.exception.MemberErrorCode;
import org.sopt.member.domain.exception.MemberException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 생성·소프트 삭제를 쓰기 트랜잭션으로 처리합니다. */
@Service
@RequiredArgsConstructor
@Transactional
public class MemberCommandService implements MemberCommandUseCase {
  private final LoadMemberPort loadMemberPort;
  private final SaveMemberPort saveMemberPort;

  @Override
  public Member createMember(String name) {
    return saveMemberPort.save(new Member(name));
  }

  @Override
  public void deleteMember(long id) {
    Member member =
        loadMemberPort
            .findActiveById(id)
            .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
    member.delete();
  }
}
