package com.talon.ats.identity.application;

import com.talon.ats.identity.contract.WorkspaceRole;
import java.time.Instant;
import java.util.UUID;

public interface TokenIssuer {

  String issueAccessToken(AccessTokenClaims claims);

  String issueRefreshToken();

  String hashRefreshToken(String rawRefreshToken);

  record AccessTokenClaims(
      UUID userId,
      UUID workspaceId,
      String workspaceName,
      WorkspaceRole role,
      String displayName,
      Instant issuedAt,
      Instant expiresAt) {}
}
