package com.talon.ats.candidates.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CandidateApplicationSummary(
    UUID applicationId,
    UUID candidateId,
    String candidateName,
    String jobTitle,
    String stage,
    String location,
    int totalExperienceMonths,
    String currentCompany,
    String currentTitle,
    List<String> skills,
    AnnualCompensation currentCompensation,
    AnnualCompensation expectedCompensation,
    int noticePeriodDays,
    LocalDate applicationDate,
    CandidateResumeStatus resumeStatus) {

  public CandidateApplicationSummary {
    Objects.requireNonNull(applicationId, "applicationId is required");
    Objects.requireNonNull(candidateId, "candidateId is required");
    Objects.requireNonNull(candidateName, "candidateName is required");
    Objects.requireNonNull(jobTitle, "jobTitle is required");
    Objects.requireNonNull(stage, "stage is required");
    Objects.requireNonNull(location, "location is required");
    Objects.requireNonNull(currentCompany, "currentCompany is required");
    Objects.requireNonNull(currentTitle, "currentTitle is required");
    skills = List.copyOf(skills);
    Objects.requireNonNull(applicationDate, "applicationDate is required");
    Objects.requireNonNull(resumeStatus, "resumeStatus is required");
    if (totalExperienceMonths < 0 || noticePeriodDays < 0) {
      throw new IllegalArgumentException("experience and notice period must not be negative");
    }
  }
}
