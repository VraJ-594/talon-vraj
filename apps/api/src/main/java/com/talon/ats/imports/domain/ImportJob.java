package com.talon.ats.imports.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ImportJob(
    UUID id,
    UUID workspaceId,
    UUID jobId,
    int rowCount,
    ImportStatus status,
    ColumnMapping mapping,
    int validRows,
    int invalidRows,
    long version,
    Instant createdAt,
    Instant updatedAt) {

  private static final int MAX_ROWS = 2_000;

  public ImportJob {
    Objects.requireNonNull(id, "id is required");
    Objects.requireNonNull(workspaceId, "workspaceId is required");
    Objects.requireNonNull(jobId, "jobId is required");
    if (rowCount < 1 || rowCount > MAX_ROWS) {
      throw new IllegalArgumentException("rowCount must be between 1 and 2000");
    }
    Objects.requireNonNull(status, "status is required");
    if (validRows < 0 || invalidRows < 0 || validRows + invalidRows > rowCount) {
      throw new IllegalArgumentException("row counts are inconsistent");
    }
    if (version < 0) {
      throw new IllegalArgumentException("version must not be negative");
    }
    Objects.requireNonNull(createdAt, "createdAt is required");
    Objects.requireNonNull(updatedAt, "updatedAt is required");
  }

  public static ImportJob uploaded(
      UUID id, UUID workspaceId, UUID jobId, int rowCount, Instant createdAt) {
    return new ImportJob(
        id,
        workspaceId,
        jobId,
        rowCount,
        ImportStatus.UPLOADED,
        null,
        0,
        0,
        0,
        createdAt,
        createdAt);
  }

  public ImportJob withMapping(ColumnMapping nextMapping, Instant changedAt) {
    requireStatus(ImportStatus.UPLOADED, ImportStatus.MAPPED);
    return copy(ImportStatus.MAPPED, Objects.requireNonNull(nextMapping), 0, 0, changedAt);
  }

  public ImportJob startValidation(Instant changedAt) {
    requireStatus(ImportStatus.MAPPED, ImportStatus.VALIDATING);
    return copy(ImportStatus.VALIDATING, mapping, 0, 0, changedAt);
  }

  public ImportJob markPreviewReady(int valid, int invalid, Instant changedAt) {
    requireStatus(ImportStatus.VALIDATING, ImportStatus.PREVIEW_READY);
    if (valid + invalid != rowCount) {
      throw new IllegalArgumentException("preview counts must equal rowCount");
    }
    return copy(ImportStatus.PREVIEW_READY, mapping, valid, invalid, changedAt);
  }

  private ImportJob copy(
      ImportStatus next,
      ColumnMapping nextMapping,
      int nextValidRows,
      int nextInvalidRows,
      Instant changedAt) {
    return new ImportJob(
        id,
        workspaceId,
        jobId,
        rowCount,
        next,
        nextMapping,
        nextValidRows,
        nextInvalidRows,
        version + 1,
        createdAt,
        Objects.requireNonNull(changedAt, "changedAt is required"));
  }

  private void requireStatus(ImportStatus expected, ImportStatus next) {
    if (status != expected) {
      throw new IllegalStateException("cannot transition import from " + status + " to " + next);
    }
  }
}
