package com.talon.ats.candidates.application;

import java.time.Instant;
import java.util.UUID;

public interface CandidateApplicationStore {

  boolean isActiveImportTarget(UUID workspaceId, UUID jobId);

  CandidateApplicationResult saveOrMatch(
      UUID workspaceId,
      UUID candidateId,
      UUID applicationId,
      CandidateApplicationCommand command,
      Instant recordedAt);
}
