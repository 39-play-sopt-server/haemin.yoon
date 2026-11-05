package org.sopt.member.adapter.out.persistence;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.sopt.member.application.port.out.LoadMemberPort;
import org.sopt.member.application.port.out.SaveMemberPort;
import org.sopt.member.domain.Member;
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
  public Member save(Member member) {
    if (member.getId() != null) {
      throw new IllegalArgumentException("신규 회원에는 ID를 지정할 수 없습니다.");
    }
    return repository.save(member);
  }
}
