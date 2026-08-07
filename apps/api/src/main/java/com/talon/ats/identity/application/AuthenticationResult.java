package com.talon.ats.identity.application;

import com.talon.ats.identity.domain.WorkspaceRole;
import java.time.Instant;
import java.util.UUID;

public record AuthenticationResult(
    UUID userId,
    UUID workspaceId,
    WorkspaceRole role,
    String displayName,
    String accessToken,
    Instant accessTokenExpiresAt,
    String refreshToken,
    Instant refreshTokenExpiresAt) {}
