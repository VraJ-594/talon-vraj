package com.talon.ats.imports.application;

import com.talon.ats.imports.domain.ImportStatus;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ImportPreviewSnapshot(
    UUID importId,
    int validCount,
    int invalidCount,
    int duplicateCount,
    List<CsvPreviewIssue> issues,
    ImportStatus status) {

  public ImportPreviewSnapshot {
    Objects.requireNonNull(importId, "importId is required");
    if (validCount < 0 || invalidCount < 0 || duplicateCount < 0) {
      throw new IllegalArgumentException("preview counts must not be negative");
    }
    issues = List.copyOf(issues);
    Objects.requireNonNull(status, "status is required");
  }
}
