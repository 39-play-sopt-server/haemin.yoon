package org.sopt.global.security;

import java.util.UUID;

public record AccessIdentity(long memberId, UUID sessionId) {}
