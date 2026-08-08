package com.talon.ats.candidates.contract;

import com.talon.ats.identity.contract.WorkspaceRole;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public interface CandidateImportAccess {

  Result createOrMatch(Actor actor, Application application);

  default UUID attachResume(Actor actor, UUID applicationId, Resume resume) {
    throw new UnsupportedOperationException("candidate resume persistence is unavailable");
  }

  record Actor(UUID userId, UUID workspaceId, WorkspaceRole role) {}

  record Money(String currency, long minorUnits) {}

  record Application(
      UUID jobId,
      String email,
      String firstName,
      String lastName,
      String phone,
      String location,
      String currentTitle,
      String currentCompany,
      String skills,
      Integer experienceMonths,
      String source,
      LocalDate appliedAt,
      Integer noticeDays,
      LocalDate availableFrom,
      Money currentCompensation,
      Money expectedCompensation,
      Map<String, String> formAnswers) {}

  record Result(
      UUID candidateId, UUID applicationId, boolean candidateCreated, boolean applicationCreated) {}

  record Resume(
      UUID fileId,
      String fileName,
      String objectKey,
      String status,
      String contentType,
      long sizeBytes) {}
}
