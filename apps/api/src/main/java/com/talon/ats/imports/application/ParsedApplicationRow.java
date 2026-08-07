package com.talon.ats.imports.application;

import java.util.Map;
import java.util.Objects;

public record ParsedApplicationRow(
    NormalizedApplicationRow row, Map<String, String> additionalAnswers) {

  public ParsedApplicationRow {
    Objects.requireNonNull(row, "row is required");
    additionalAnswers = Map.copyOf(additionalAnswers);
  }
}
