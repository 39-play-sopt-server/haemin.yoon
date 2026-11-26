package org.sopt.member.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.sopt.member.application.port.out.LoadMemberPort;
import org.sopt.member.application.port.out.SaveMemberPort;
import org.sopt.member.domain.Member;
import org.sopt.member.domain.exception.MemberErrorCode;
import org.sopt.member.domain.exception.MemberException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberPersistenceAdapter implements LoadMemberPort, SaveMemberPort {
  private final MemberRepository repository;

  @Override
  public Optional<Member> findActiveById(long id) {
    return repository.findByIdAndDeletedFalse(id);
  }

  @Override
  public Optional<Member> findActiveByEmail(String normalizedEmail) {
    return repository.findByEmailAndDeletedFalse(normalizedEmail);
  }

  @Override
  public boolean existsByEmail(String normalizedEmail) {
    return repository.existsByEmail(normalizedEmail);
  }

  @Override
  public Member save(Member member) {
    if (member.getId() != null) {
      throw new IllegalArgumentException("신규 회원에는 ID를 지정할 수 없습니다.");
    }
    try {
      // 회원 테이블의 유일 제약은 이메일뿐이므로, 사전 확인 이후 동시 가입한 경우만 여기서 실패합니다.
      return repository.saveAndFlush(member);
    } catch (DataIntegrityViolationException exception) {
      throw new MemberException(MemberErrorCode.MEMBER_EMAIL_DUPLICATED);
    }
  }

  public List<Member> listActive() {
    return repository.findByDeletedFalseOrderByIdAsc();
  }
}
