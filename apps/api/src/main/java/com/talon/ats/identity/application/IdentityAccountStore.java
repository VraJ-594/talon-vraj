package com.talon.ats.identity.application;

import com.talon.ats.identity.domain.RefreshSession;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdentityAccountStore {

  Optional<AuthenticationAccount> findByNormalizedEmail(String normalizedEmail);

  void completeSuccessfulLogin(RefreshSession refreshSession, Instant loggedInAt);

  Optional<AuthenticationAccount> rotateRefreshSession(
      String currentTokenHash,
      UUID nextSessionId,
      String nextTokenHash,
      Instant nextExpiresAt,
      Instant rotatedAt);

  void revokeRefreshSessionFamily(String currentTokenHash, Instant revokedAt);
}
