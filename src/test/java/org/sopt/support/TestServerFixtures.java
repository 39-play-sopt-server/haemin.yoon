package org.sopt.support;

import org.sopt.global.exception.GlobalExceptionHandler;
import org.sopt.global.exception.BaseException;
import org.sopt.global.exception.GlobalErrorCode;
import org.sopt.post.adapter.out.id.SequentialPostIdGenerator;
import org.sopt.post.domain.Post;
import org.sopt.post.adapter.in.api.PostCommandController;
import org.sopt.post.adapter.in.api.PostQueryController;
import org.sopt.post.adapter.out.persistence.InMemoryPostRepository;
import org.sopt.post.application.service.command.PostCommandService;
import org.sopt.post.application.service.query.PostQueryService;
import org.sopt.post.application.port.out.PostIdGeneratorPort;
import org.sopt.post.config.PostServerConfiguration.Controllers;

public final class TestServerFixtures {
  private TestServerFixtures() {}

  public static Controllers withFailingExistenceCheck() {
    var repository = new InMemoryPostRepository();
    var queries = new PostQueryService(repository) {
      @Override
      public boolean hasPosts() {
        throw new BaseException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
      }
    };
    return controllers(repository, new SequentialPostIdGenerator(), queries);
  }

  public static Controllers withFailingRead() {
    var repository = new InMemoryPostRepository();
    var queries = new PostQueryService(repository) {
      @Override
      public Post getPost(long id) {
        throw new BaseException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
      }
    };
    return controllers(repository, new SequentialPostIdGenerator(), queries);
  }

  public static Controllers withFixedId(long id) {
    var repository = new InMemoryPostRepository();
    return controllers(repository, () -> id, new PostQueryService(repository));
  }

  public static Controllers withRepository(InMemoryPostRepository repository) {
    return controllers(repository, new SequentialPostIdGenerator(), new PostQueryService(repository));
  }

  private static Controllers controllers(InMemoryPostRepository repository,
      PostIdGeneratorPort idGenerator, PostQueryService queries) {
    var handler = new GlobalExceptionHandler();
    return new Controllers(
        new PostCommandController(new PostCommandService(repository, repository, idGenerator), handler),
        new PostQueryController(queries, handler));
  }
}
