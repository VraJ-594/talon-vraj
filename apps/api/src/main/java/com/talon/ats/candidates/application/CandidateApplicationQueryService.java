package com.talon.ats.candidates.application;

import com.talon.ats.identity.contract.WorkspaceRole;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

public final class CandidateApplicationQueryService {

  private final CandidateApplicationQueryStore store;

  public CandidateApplicationQueryService(CandidateApplicationQueryStore store) {
    this.store = Objects.requireNonNull(store, "store is required");
  }

  public CandidateApplicationPage list(Actor actor, String cursor, int limit) {
    authorize(actor);
    if (limit < 1 || limit > 100) {
      throw new CandidateQueryException(
          "CANDIDATE_PAGE_INVALID", "Candidate page size must be between 1 and 100");
    }
    CandidateApplicationSlice slice = store.list(actor.workspaceId(), decode(cursor), limit);
    return new CandidateApplicationPage(slice.items(), encode(slice.nextCursor()));
  }

  public CandidateApplicationDetail detail(Actor actor, UUID applicationId) {
    authorize(actor);
    Objects.requireNonNull(applicationId, "applicationId is required");
    return store
        .findDetail(actor.workspaceId(), applicationId)
        .orElseThrow(
            () ->
                new CandidateQueryException(
                    "CANDIDATE_APPLICATION_NOT_FOUND", "Candidate application was not found"));
  }

  public CandidateResume resume(Actor actor, UUID applicationId) {
    authorize(actor);
    Objects.requireNonNull(applicationId, "applicationId is required");
    return store
        .findCleanResume(actor.workspaceId(), applicationId)
        .orElseThrow(
            () ->
                new CandidateQueryException(
                    "RESUME_NOT_CLEAN", "Candidate resume is not available for download"));
  }

  private static void authorize(Actor actor) {
    Objects.requireNonNull(actor, "actor is required");
    if (actor.role() != WorkspaceRole.WORKSPACE_ADMIN && actor.role() != WorkspaceRole.RECRUITER) {
      throw new CandidateQueryException("CANDIDATE_FORBIDDEN", "Recruiting access is required");
    }
  }

  private static String encode(CandidateApplicationCursor cursor) {
    if (cursor == null) {
      return null;
    }
    String raw = cursor.appliedAt() + "\n" + cursor.applicationId();
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  private static CandidateApplicationCursor decode(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    try {
      String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      String[] parts = raw.split("\\n", -1);
      if (parts.length != 2) {
        throw new IllegalArgumentException("cursor field count");
      }
      return new CandidateApplicationCursor(LocalDate.parse(parts[0]), UUID.fromString(parts[1]));
    } catch (RuntimeException exception) {
      throw new CandidateQueryException(
          "CANDIDATE_CURSOR_INVALID", "Candidate page cursor is invalid");
    }
  }

  public record Actor(UUID userId, UUID workspaceId, WorkspaceRole role) {

    public Actor {
      Objects.requireNonNull(userId, "userId is required");
      Objects.requireNonNull(workspaceId, "workspaceId is required");
      Objects.requireNonNull(role, "role is required");
    }
  }
}
