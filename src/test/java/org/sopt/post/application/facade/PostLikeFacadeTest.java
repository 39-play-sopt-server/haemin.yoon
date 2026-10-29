package org.sopt.post.application.facade;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.sopt.post.application.port.in.command.PostLikeCommandUseCase;
import org.sopt.post.application.port.in.command.dto.PostLikeResult;
import org.sopt.post.domain.exception.PostErrorCode;
import org.sopt.post.domain.exception.PostException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;

class PostLikeFacadeTest {
  final PostLikeCommandUseCase commands = mock(PostLikeCommandUseCase.class);
  final PostLikeFacade facade = new PostLikeFacade(commands);

  @Test
  void retriesOnlyTransientLockFailuresAndReturnsSuccess() {
    when(commands.like(1, 2))
        .thenThrow(new CannotAcquireLockException("deadlock"))
        .thenThrow(new QueryTimeoutException("lock query timeout"))
        .thenReturn(new PostLikeResult(true, 1));
    assertEquals(new PostLikeResult(true, 1), facade.like(1, 2));
    verify(commands, times(3)).like(1, 2);
  }

  @Test
  void exhaustedRetriesBecomeServiceUnavailable() {
    when(commands.unlike(1, 2)).thenThrow(new CannotAcquireLockException("deadlock"));
    var failure = assertThrows(PostException.class, () -> facade.unlike(1, 2));
    assertEquals(PostErrorCode.POST_LIKE_BUSY, failure.getErrorCode());
    verify(commands, times(3)).unlike(1, 2);
  }

  @Test
  void doesNotRetryBusinessOrIntegrityFailures() {
    when(commands.like(1, 2)).thenThrow(new PostException(PostErrorCode.POST_NOT_FOUND));
    assertThrows(PostException.class, () -> facade.like(1, 2));
    verify(commands).like(1, 2);
    when(commands.unlike(1, 2)).thenThrow(new DataIntegrityViolationException("unexpected"));
    assertThrows(DataIntegrityViolationException.class, () -> facade.unlike(1, 2));
    verify(commands).unlike(1, 2);
  }
}
