package org.sopt.client.console;

/**
 * 콘솔 입력 종료를 실행 루프에 전달하는 클라이언트 전용 예외입니다.
 * 중첩된 입력·재입력 과정에서도 즉시 빠져나와, 미완성 요청을 보내지 않고 정상 종료하게 합니다.
 */
public class ConsoleInputClosedException extends RuntimeException {
}
