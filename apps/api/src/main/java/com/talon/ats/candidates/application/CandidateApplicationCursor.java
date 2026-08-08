package com.talon.ats.candidates.application;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record CandidateApplicationCursor(LocalDate appliedAt, UUID applicationId) {

  public CandidateApplicationCursor {
    Objects.requireNonNull(appliedAt, "appliedAt is required");
    Objects.requireNonNull(applicationId, "applicationId is required");
  }
}
