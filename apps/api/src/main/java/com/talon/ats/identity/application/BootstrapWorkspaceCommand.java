package com.talon.ats.identity.application;

public record BootstrapWorkspaceCommand(
    String cognitoSubject,
    String email,
    String displayName,
    String workspaceName,
    String workspaceSlug,
    String defaultTimezone) {}
