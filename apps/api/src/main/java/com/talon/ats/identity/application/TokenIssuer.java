package com.talon.ats.identity.application;

import com.talon.ats.identity.domain.WorkspaceRole;
import java.time.Instant;
import java.util.UUID;

public interface TokenIssuer {

  String issueAccessToken(AccessTokenClaims claims);

  String issueRefreshToken();

  String hashRefreshToken(String rawRefreshToken);

  record AccessTokenClaims(
      UUID userId, UUID workspaceId, WorkspaceRole role, Instant issuedAt, Instant expiresAt) {}
}
