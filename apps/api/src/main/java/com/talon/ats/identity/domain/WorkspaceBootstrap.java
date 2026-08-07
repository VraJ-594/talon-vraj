package com.talon.ats.identity.domain;

import java.util.Objects;

public record WorkspaceBootstrap(
    AppUser user, Workspace workspace, WorkspaceMembership membership) {

  public WorkspaceBootstrap {
    Objects.requireNonNull(user);
    Objects.requireNonNull(workspace);
    Objects.requireNonNull(membership);
    if (!workspace.createdBy().equals(user.id())
        || !membership.userId().equals(user.id())
        || !membership.workspaceId().equals(workspace.id())) {
      throw new IllegalArgumentException("Workspace bootstrap references must be consistent");
    }
  }
}
