package com.talon.ats.imports.application;

import com.talon.ats.imports.domain.ImportStatus;
import java.util.List;
import java.util.UUID;

public record ImportProgressSnapshot(
    UUID importId,
    ImportStatus status,
    int processedCount,
    int totalCount,
    boolean errorCsvAvailable,
    List<Row> rows) {

  public ImportProgressSnapshot {
    rows = List.copyOf(rows);
  }

  public record Row(int rowNumber, String status, boolean retryable, String code, String message) {}
}
