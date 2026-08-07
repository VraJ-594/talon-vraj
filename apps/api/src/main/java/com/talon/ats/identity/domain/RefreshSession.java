package com.talon.ats.identity.domain;

import java.time.Instant;
import java.util.UUID;

public record RefreshSession(
    UUID id,
    UUID userId,
    UUID workspaceId,
    String tokenHash,
    UUID familyId,
    UUID parentId,
    Instant expiresAt,
    Instant usedAt,
    Instant revokedAt,
    Instant createdAt) {}
