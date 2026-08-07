package com.talon.ats.identity.application;

import com.talon.ats.identity.domain.AppUser;
import com.talon.ats.identity.domain.WorkspaceMembership;
import java.util.Objects;

public record AuthenticationAccount(
    AppUser user, WorkspaceMembership membership, String workspaceName) {

  public AuthenticationAccount {
    Objects.requireNonNull(user, "user is required");
    Objects.requireNonNull(membership, "membership is required");
    if (workspaceName == null || workspaceName.isBlank()) {
      throw new IllegalArgumentException("workspaceName is required");
    }
    if (!user.id().equals(membership.userId())) {
      throw new IllegalArgumentException("membership must belong to the account user");
    }
  }
}
