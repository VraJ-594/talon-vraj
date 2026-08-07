package com.talon.ats.imports.application;

import java.util.List;

public record CsvParseResult(
    int validCount,
    int invalidCount,
    int duplicateCount,
    List<ParsedApplicationRow> validRows,
    List<CsvPreviewIssue> issues) {

  public CsvParseResult {
    if (validCount < 0 || invalidCount < 0 || duplicateCount < 0) {
      throw new IllegalArgumentException("preview counts must not be negative");
    }
    validRows = List.copyOf(validRows);
    issues = List.copyOf(issues);
    if (validRows.size() != validCount) {
      throw new IllegalArgumentException("validCount must match validRows");
    }
  }
}
