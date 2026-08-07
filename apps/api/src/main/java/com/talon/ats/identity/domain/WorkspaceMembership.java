package com.talon.ats.identity.domain;

import com.talon.ats.identity.contract.WorkspaceRole;
import java.time.Instant;
import java.util.UUID;

public record WorkspaceMembership(
    UUID id,
    UUID workspaceId,
    UUID userId,
    WorkspaceRole role,
    WorkspaceMembershipStatus status,
    Instant joinedAt,
    long version) {}
