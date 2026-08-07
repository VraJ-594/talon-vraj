package com.talon.ats.imports.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ImportRow(
    UUID importId, int rowNumber, ImportRowStatus status, Instant createdAt, Instant updatedAt) {

  public ImportRow {
    Objects.requireNonNull(importId, "importId is required");
    if (rowNumber < 1) {
      throw new IllegalArgumentException("rowNumber must be positive");
    }
    Objects.requireNonNull(status, "status is required");
    Objects.requireNonNull(createdAt, "createdAt is required");
    Objects.requireNonNull(updatedAt, "updatedAt is required");
  }

  public ImportRow transitionTo(ImportRowStatus next, Instant changedAt) {
    Objects.requireNonNull(next, "next status is required");
    Objects.requireNonNull(changedAt, "changedAt is required");
    if (terminal(status)) {
      throw new IllegalStateException("terminal import row cannot transition from " + status);
    }
    return new ImportRow(importId, rowNumber, next, createdAt, changedAt);
  }

  private static boolean terminal(ImportRowStatus value) {
    return switch (value) {
      case COMPLETED,
              INVALID,
              DUPLICATE_APPLICATION,
              SOURCE_AUTH_REQUIRED,
              RESUME_FETCH_FAILED,
              UNSAFE_FILE,
              PERSISTENCE_FAILED,
              CANCELLED ->
          true;
      default -> false;
    };
  }
}
