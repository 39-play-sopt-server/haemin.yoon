package org.sopt.support;

import java.util.UUID;
import org.sopt.member.application.port.in.command.MemberCommandUseCase;
import org.sopt.member.application.port.in.dto.MemberInfo;
import org.springframework.test.context.DynamicPropertyRegistry;

/** PostgreSQL 통합 테스트는 test 프로필을 사용하지 않으므로 테스트 전용 JWT 키와 회원 등록 방법을 공유합니다. */
public final class PostgresTestSupport {
  private static final String JWT_SECRET = "cG9zdGdyZXMtdGVzdC1vbmx5LWp3dC1zZWNyZXQta2V5LTMyYg==";

  private PostgresTestSupport() {}

  public static void jwtProperties(DynamicPropertyRegistry properties) {
    properties.add("jwt.secret", () -> JWT_SECRET);
  }

  public static MemberInfo register(MemberCommandUseCase members, String name) {
    return members.registerMember(
        name, "member-" + UUID.randomUUID() + "@example.com", "{noop}test-password");
  }
}
