package com.talon.ats.identity.application;

import java.util.UUID;

public record BootstrapWorkspaceResult(UUID workspaceId, UUID membershipId) {}
