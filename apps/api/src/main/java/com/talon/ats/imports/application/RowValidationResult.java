package com.talon.ats.imports.application;

import java.util.List;
import java.util.Optional;

public final class RowValidationResult {

  private final int rowNumber;
  private final NormalizedApplicationRow row;
  private final List<RowValidationError> errors;

  public RowValidationResult(
      int rowNumber, NormalizedApplicationRow row, List<RowValidationError> errors) {
    this.rowNumber = rowNumber;
    this.row = row;
    this.errors = List.copyOf(errors);
    if ((row == null) == this.errors.isEmpty()) {
      throw new IllegalArgumentException("a validation result must contain either a row or errors");
    }
  }

  public int rowNumber() {
    return rowNumber;
  }

  public boolean valid() {
    return row != null;
  }

  public Optional<NormalizedApplicationRow> row() {
    return Optional.ofNullable(row);
  }

  public List<RowValidationError> errors() {
    return errors;
  }
}
