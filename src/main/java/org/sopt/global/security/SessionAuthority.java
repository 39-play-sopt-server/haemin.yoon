package org.sopt.global.security;

import java.util.Optional;

public interface SessionAuthority {
  Optional<String> currentRole(AccessIdentity identity);
}
