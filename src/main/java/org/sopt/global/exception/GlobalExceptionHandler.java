package org.sopt.global.exception;

import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sopt.global.response.BaseResponse;

/**
 * 컨트롤러의 서버 작업을 실행하고 성공·예외를 일관된 BaseResponse로 변환합니다.
 * 각 컨트롤러 메서드에 같은 try-catch가 반복되지 않도록 실행할 작업을 전달받습니다.
 * 현재는 직접 호출하는 실행 래퍼이며, 프레임워크가 자동으로 적용하는 예외 핸들러는 아닙니다.
 */
public class GlobalExceptionHandler {
  private static final Logger LOGGER = Logger.getLogger(GlobalExceptionHandler.class.getName());

  /**
   * 결과가 있는 작업을 처리합니다. 예상된 실패는 해당 오류 코드를 그대로 응답합니다.
   * 예상 밖의 RuntimeException은 서버에 원인을 기록하고 클라이언트에는 일반 메시지만 전달합니다.
   * 복구를 전제로 하지 않는 Error까지 잡지는 않습니다.
   */
  public <T> BaseResponse<T> handle(Supplier<T> action, String successMessage) {
    try {
      return BaseResponse.success(action.get(), successMessage);
    } catch (BaseException e) {
      return BaseResponse.failure(e.getErrorCode());
    } catch (RuntimeException e) {
      LOGGER.log(Level.SEVERE, "서버 요청 처리 중 예기치 않은 오류가 발생했습니다.", e);
      return BaseResponse.failure(GlobalErrorCode.INTERNAL_SERVER_ERROR);
    }
  }

  /** 반환값 없는 생성·수정·삭제도 같은 처리 흐름을 사용하며, 성공 데이터는 null로 반환합니다. */
  public BaseResponse<Void> handle(Runnable action, String successMessage) {
    return handle(() -> {
      action.run();
      return null;
    }, successMessage);
  }
}
