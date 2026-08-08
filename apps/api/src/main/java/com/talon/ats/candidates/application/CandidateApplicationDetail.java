package com.talon.ats.candidates.application;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

public record CandidateApplicationDetail(
    CandidateApplicationSummary summary,
    String email,
    String maskedPhone,
    String source,
    LocalDate availableFrom,
    Map<String, String> additionalAnswers,
    String resumeFileName,
    boolean resumeDownloadAllowed) {

  public CandidateApplicationDetail {
    Objects.requireNonNull(summary, "summary is required");
    Objects.requireNonNull(email, "email is required");
    Objects.requireNonNull(maskedPhone, "maskedPhone is required");
    Objects.requireNonNull(source, "source is required");
    additionalAnswers = Map.copyOf(additionalAnswers);
    Objects.requireNonNull(resumeFileName, "resumeFileName is required");
  }
}
