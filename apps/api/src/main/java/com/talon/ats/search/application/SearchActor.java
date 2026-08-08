package com.talon.ats.search.application;

import com.talon.ats.identity.contract.WorkspaceRole;
import java.util.UUID;

public record SearchActor(UUID userId, UUID workspaceId, WorkspaceRole role) {}
