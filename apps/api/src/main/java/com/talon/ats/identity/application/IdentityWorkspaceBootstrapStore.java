package com.talon.ats.identity.application;

import com.talon.ats.identity.domain.WorkspaceBootstrap;

public interface IdentityWorkspaceBootstrapStore {

  boolean hasMembershipByNormalizedEmail(String normalizedEmail);

  void save(WorkspaceBootstrap workspaceBootstrap);
}
