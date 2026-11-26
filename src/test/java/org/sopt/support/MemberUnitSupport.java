package org.sopt.support;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.sopt.member.application.port.in.command.MemberCommandUseCase;
import org.sopt.member.application.port.in.dto.MemberInfo;
import org.sopt.member.application.port.in.query.MemberQueryUseCase;
import org.sopt.member.application.port.out.LoadMemberPort;
import org.sopt.member.application.port.out.SaveMemberPort;
import org.sopt.member.application.service.command.MemberCommandService;
import org.sopt.member.application.service.query.MemberQueryService;
import org.sopt.member.domain.Member;
import org.springframework.test.util.ReflectionTestUtils;

public abstract class MemberUnitSupport {
  private final Members repository = new Members();
  protected final MemberCommandUseCase memberCommands =
      new MemberCommandService(repository, repository);
  protected final MemberQueryUseCase memberQueries = new MemberQueryService(repository);

  protected MemberUnitSupport() {
    createMember("작성자");
  }

  protected MemberInfo createMember(String name) {
    return memberCommands.registerMember(
        name, "member-" + UUID.randomUUID() + "@example.com", "{noop}test-password");
  }

  protected long memberId(String name) {
    return createMember(name).id();
  }

  private static class Members implements LoadMemberPort, SaveMemberPort {
    public List<Member> listActive() {
      return members.values().stream().filter(m -> !m.isDeleted()).toList();
    }

    private final HashMap<Long, Member> members = new HashMap<>();

    @Override
    public Optional<Member> findActiveById(long id) {
      return Optional.ofNullable(members.get(id)).filter(member -> !member.isDeleted());
    }

    @Override
    public Optional<Member> findActiveByEmail(String normalizedEmail) {
      return members.values().stream()
          .filter(member -> !member.isDeleted() && normalizedEmail.equals(member.getEmail()))
          .findFirst();
    }

    @Override
    public boolean existsByEmail(String normalizedEmail) {
      return members.values().stream()
          .anyMatch(member -> normalizedEmail.equals(member.getEmail()));
    }

    @Override
    public Member save(Member member) {
      ReflectionTestUtils.setField(member, "id", (long) members.size() + 1);
      members.put(member.getId(), member);
      return member;
    }
  }
}
