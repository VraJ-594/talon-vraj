package com.talon.ats.candidates.application;

import java.util.Objects;
import java.util.UUID;

public record CandidateApplicationResult(
    UUID candidateId, UUID applicationId, boolean candidateCreated, boolean applicationCreated) {

  public CandidateApplicationResult {
    Objects.requireNonNull(candidateId, "candidateId is required");
    Objects.requireNonNull(applicationId, "applicationId is required");
  }
}
