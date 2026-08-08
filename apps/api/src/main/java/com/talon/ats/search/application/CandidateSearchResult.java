package com.talon.ats.search.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CandidateSearchResult(
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
    AnnualCompensationView currentCompensation,
    AnnualCompensationView expectedCompensation,
    int noticePeriodDays,
    LocalDate applicationDate,
    String resumeStatus) {

  public CandidateSearchResult {
    skills = List.copyOf(skills);
  }
}
