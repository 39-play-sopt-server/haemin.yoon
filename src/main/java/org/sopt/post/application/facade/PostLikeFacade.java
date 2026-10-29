package org.sopt.post.application.facade;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sopt.post.application.port.in.command.PostLikeCommandUseCase;
import org.sopt.post.application.port.in.command.PostLikeResult;
import org.sopt.post.domain.exception.PostErrorCode;
import org.sopt.post.domain.exception.PostException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 재시도는 트랜잭션 밖에서 실행하여 롤백된 트랜잭션을 재사용하지 않습니다. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NEVER)
public class PostLikeFacade {
  private static final Logger LOGGER = LoggerFactory.getLogger(PostLikeFacade.class);
  private static final int MAX_ATTEMPTS = 3;
  private final PostLikeCommandUseCase commands;

  public PostLikeResult like(long postId, long memberId) {
    return retry(() -> commands.like(postId, memberId));
  }

  public PostLikeResult unlike(long postId, long memberId) {
    return retry(() -> commands.unlike(postId, memberId));
  }

  private PostLikeResult retry(Supplier<PostLikeResult> action) {
    for (int attempt = 1; ; attempt++) {
      try {
        return action.get();
      } catch (PessimisticLockingFailureException | QueryTimeoutException exception) {
        if (attempt == MAX_ATTEMPTS) {
          LOGGER.warn("좋아요 잠금 충돌 재시도 소진: attempts={}", attempt, exception);
          throw new PostException(PostErrorCode.POST_LIKE_BUSY);
        }
        try {
          Thread.sleep(ThreadLocalRandom.current().nextLong(10, 31) * attempt);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          throw new PostException(PostErrorCode.POST_LIKE_BUSY);
        }
      }
    }
  }
}
