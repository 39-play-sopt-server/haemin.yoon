package org.sopt.post.adapter.out.id;

import org.sopt.post.application.port.out.PostIdGeneratorPort;

/**
 * ID 발급 포트를 구현하는 메모리 기반 출력 어댑터로, 1부터 증가하는 ID를 발급합니다.
 * 목록 위치와 ID를 분리해 삭제 뒤에도 기존 ID가 유지되며, 발급된 ID는 다시 사용하지 않습니다.
 * 단일 스레드 실행용이며, 객체를 새로 만들거나 프로그램을 재시작하면 발급 상태가 초기화됩니다.
 */
public class SequentialPostIdGenerator implements PostIdGeneratorPort {
  private long lastId = 0;

  @Override
  public long nextId() {
    // 범위를 넘으면 음수로 순환시키지 않고 실패하게 해 중복 ID 발급을 방지합니다.
    lastId = Math.incrementExact(lastId);
    return lastId;
  }
}
