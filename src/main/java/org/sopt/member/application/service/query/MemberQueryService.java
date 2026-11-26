package org.sopt.member.application.service.query;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.sopt.member.application.port.in.dto.MemberCredential;
import org.sopt.member.application.port.in.dto.MemberInfo;
import org.sopt.member.application.port.in.query.MemberQueryUseCase;
import org.sopt.member.application.port.out.LoadMemberPort;
import org.sopt.member.domain.Member;
import org.sopt.member.domain.exception.MemberErrorCode;
import org.sopt.member.domain.exception.MemberException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 활성 회원 조회를 읽기 전용 트랜잭션으로 처리합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberQueryService implements MemberQueryUseCase {
  private final LoadMemberPort loadMemberPort;

  @Override
  public MemberInfo getActiveMember(long id) {
    return loadMemberPort
        .findActiveById(id)
        .map(member -> new MemberInfo(member.getId(), member.getName(), member.getEmail()))
        .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
  }

  @Override
  public Optional<MemberCredential> findActiveCredentialByEmail(String email) {
    String normalized;
    try {
      normalized = Member.normalizeEmail(email);
    } catch (MemberException invalidEmail) {
      return Optional.empty();
    }
    return loadMemberPort
        .findActiveByEmail(normalized)
        .map(member -> new MemberCredential(member.getId(), member.getPasswordHash()));
  }

  public List<MemberInfo> listActive() {
    return loadMemberPort.listActive().stream()
        .map(m -> new MemberInfo(m.getId(), m.getName(), m.getEmail()))
        .toList();
  }
}
