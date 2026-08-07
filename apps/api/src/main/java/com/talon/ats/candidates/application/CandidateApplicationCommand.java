package com.talon.ats.candidates.application;

import java.util.Objects;
import java.util.UUID;

public record CandidateApplicationCommand(
    UUID jobId, CandidateData candidate, ApplicationData application) {

  public CandidateApplicationCommand {
    Objects.requireNonNull(jobId, "jobId is required");
    Objects.requireNonNull(candidate, "candidate is required");
    Objects.requireNonNull(application, "application is required");
  }
}
