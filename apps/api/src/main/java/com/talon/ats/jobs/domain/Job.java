package com.talon.ats.jobs.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Job(
    UUID id,
    UUID workspaceId,
    String title,
    String department,
    String location,
    JobStatus status,
    long version,
    Instant createdAt,
    Instant updatedAt) {

  public Job {
    Objects.requireNonNull(id, "id is required");
    Objects.requireNonNull(workspaceId, "workspaceId is required");
    title = required(title, "title");
    department = optional(department);
    location = optional(location);
    Objects.requireNonNull(status, "status is required");
    Objects.requireNonNull(createdAt, "createdAt is required");
    Objects.requireNonNull(updatedAt, "updatedAt is required");
    if (version < 0) {
      throw new IllegalArgumentException("version must not be negative");
    }
  }

  private static String required(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return value.trim();
  }

  private static String optional(String value) {
    return value == null ? "" : value.trim();
  }
}
