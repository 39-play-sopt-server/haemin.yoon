package org.sopt.global.exception;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.sopt.post.domain.exception.PostErrorCode;
import org.sopt.post.domain.exception.PostException;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {
  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void returnsSuccessDataAndMessage() {
    var response = handler.handle(() -> "결과", "조회 성공");
    assertTrue(response.success());
    assertEquals("SUCCESS", response.code());
    assertEquals("조회 성공", response.message());
    assertEquals("결과", response.data());
  }

  @Test
  void returnsVoidSuccess() {
    List<String> saved = new ArrayList<>();
    var response = handler.handle((Runnable) () -> saved.add("게시글"), "작성 성공");
    assertTrue(response.success());
    assertNull(response.data());
    assertEquals(List.of("게시글"), saved);
  }

  @Test
  void translatesDomainErrorIntoFailure() {
    var response = handler.handle((Runnable) () -> {
      throw new PostException(PostErrorCode.POST_NOT_FOUND);
    }, "성공");
    assertFalse(response.success());
    assertEquals("POST_NOT_FOUND", response.code());
    assertEquals("존재하지 않는 게시글입니다.", response.message());
    assertNull(response.data());
  }

  @Test
  void hidesUnexpectedErrorDetailsAndLogsCause() {
    var cause = new IllegalStateException("internal secret details");
    Logger logger = Logger.getLogger(GlobalExceptionHandler.class.getName());
    boolean original = logger.getUseParentHandlers();
    List<LogRecord> records = new ArrayList<>();
    Handler capture = new Handler() {
      public void publish(LogRecord record) { records.add(record); }
      public void flush() {}
      public void close() {}
    };
    logger.setUseParentHandlers(false);
    logger.addHandler(capture);
    try {
      var response = handler.handle((Runnable) () -> { throw cause; }, "성공");
      assertFalse(response.success());
      assertEquals("INTERNAL_SERVER_ERROR", response.code());
      assertEquals("서버 오류가 발생했습니다.", response.message());
      assertNull(response.data());
      assertEquals(1, records.size());
      assertSame(cause, records.get(0).getThrown());
    } finally {
      logger.removeHandler(capture);
      logger.setUseParentHandlers(original);
    }
  }

  @Test
  void doesNotCatchErrors() {
    AssertionError cause = new AssertionError("fatal");
    assertSame(cause, assertThrows(AssertionError.class,
        () -> handler.handle((Runnable) () -> { throw cause; }, "성공")));
  }
}
