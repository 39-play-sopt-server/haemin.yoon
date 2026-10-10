package org.sopt.post.application.port.out;

/**
 * 애플리케이션이 외부 ID 발급 기능에 요구하는 계약인 출력 포트입니다.
 * out은 애플리케이션이 다른 기능을 호출하는 방향을 뜻합니다.
 * 발급 방식을 서비스에서 분리해 순차 ID 등의 구현을 교체할 수 있게 합니다.
 */
public interface PostIdGeneratorPort {
  /** 발급기 수명 동안 이미 발급한 ID와 중복되지 않는 새 ID를 반환합니다. */
  long nextId();
}
