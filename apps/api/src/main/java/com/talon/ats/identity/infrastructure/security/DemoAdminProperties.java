package com.talon.ats.identity.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("talon.demo-admin")
record DemoAdminProperties(
    String email,
    String displayName,
    String passwordHash,
    String workspaceName,
    String workspaceSlug,
    String defaultTimezone) {

  DemoAdminProperties {
    email = required(email, "email");
    passwordHash = required(passwordHash, "password-hash");
    displayName = defaulted(displayName, "Demo Administrator");
    workspaceName = defaulted(workspaceName, "Talon Demo");
    workspaceSlug = defaulted(workspaceSlug, "talon-demo");
    defaultTimezone = defaulted(defaultTimezone, "Asia/Kolkata");
  }

  private static String required(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return value.trim();
  }

  private static String defaulted(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }
}
