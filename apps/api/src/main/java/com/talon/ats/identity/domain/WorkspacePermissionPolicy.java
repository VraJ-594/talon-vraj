package com.talon.ats.identity.domain;

import com.talon.ats.identity.contract.WorkspaceRole;
import java.util.Objects;

public final class WorkspacePermissionPolicy {

  private WorkspacePermissionPolicy() {}

  public static boolean canManageMembers(WorkspaceRole role) {
    return Objects.requireNonNull(role) == WorkspaceRole.WORKSPACE_ADMIN;
  }
}
