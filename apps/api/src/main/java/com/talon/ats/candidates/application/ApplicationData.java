package com.talon.ats.candidates.application;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

public record ApplicationData(
    String source,
    LocalDate appliedAt,
    Integer noticeDays,
    LocalDate availableFrom,
    AnnualCompensation currentCompensation,
    AnnualCompensation expectedCompensation,
    Map<String, String> formAnswers) {

  public ApplicationData {
    if (source == null || source.isBlank()) {
      throw new IllegalArgumentException("source is required");
    }
    source = source.trim();
    Objects.requireNonNull(appliedAt, "appliedAt is required");
    if (noticeDays != null && noticeDays < 0) {
      throw new IllegalArgumentException("noticeDays must not be negative");
    }
    formAnswers = formAnswers == null ? Map.of() : Map.copyOf(formAnswers);
  }
}
