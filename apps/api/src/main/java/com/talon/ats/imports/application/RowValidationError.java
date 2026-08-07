package com.talon.ats.imports.application;

import com.talon.ats.imports.domain.CanonicalField;
import java.util.Objects;

public record RowValidationError(CanonicalField field, String code, String message) {
  public RowValidationError {
    Objects.requireNonNull(field, "field is required");
    Objects.requireNonNull(code, "code is required");
    Objects.requireNonNull(message, "message is required");
  }
}
