package com.talon.ats.identity.application;

import com.talon.ats.identity.domain.RefreshSession;
import java.time.Instant;
import java.util.Optional;

public interface IdentityAccountStore {

  Optional<AuthenticationAccount> findByNormalizedEmail(String normalizedEmail);

  void completeSuccessfulLogin(RefreshSession refreshSession, Instant loggedInAt);
}
