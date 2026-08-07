package com.talon.ats.identity.application;

public record BootstrapWorkspaceCommand(
    String email,
    String displayName,
    String passwordHash,
    String workspaceName,
    String workspaceSlug,
    String defaultTimezone) {}
