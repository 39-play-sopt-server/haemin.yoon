package org.sopt.auth.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.sopt.global.security.AccessIdentity;

public interface LoginSessions {
  void open(AccessIdentity identity, String hash, Instant expiry);

  record Session(AccessIdentity identity, Instant expiry) {}

  Optional<Session> rotate(String previous, String next);

  void close(UUID sessionId);
}
