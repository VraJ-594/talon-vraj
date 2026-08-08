package com.talon.ats.candidates.application;

import java.util.Optional;
import java.util.UUID;

public interface CandidateApplicationQueryStore {

  CandidateApplicationSlice list(UUID workspaceId, CandidateApplicationCursor cursor, int limit);

  Optional<CandidateApplicationDetail> findDetail(UUID workspaceId, UUID applicationId);

  Optional<CandidateResume> findCleanResume(UUID workspaceId, UUID applicationId);
}
