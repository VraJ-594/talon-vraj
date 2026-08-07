package com.talon.ats.candidates.application;

import com.talon.ats.identity.contract.WorkspaceRole;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class CandidateApplicationService {

  private final CandidateApplicationStore store;
  private final Supplier<UUID> idGenerator;
  private final Clock clock;

  public CandidateApplicationService(
      CandidateApplicationStore store, Supplier<UUID> idGenerator, Clock clock) {
    this.store = Objects.requireNonNull(store);
    this.idGenerator = Objects.requireNonNull(idGenerator);
    this.clock = Objects.requireNonNull(clock);
  }

  public CandidateApplicationResult createOrMatch(
      Actor actor, CandidateApplicationCommand command) {
    requireRecruitingAccess(actor);
    Objects.requireNonNull(command, "command is required");
    if (!store.isActiveImportTarget(actor.workspaceId(), command.jobId())) {
      throw new IllegalArgumentException("job is not an active import target");
    }
    return store.saveOrMatch(actor.workspaceId(), nextId(), nextId(), command, clock.instant());
  }

  private UUID nextId() {
    return Objects.requireNonNull(idGenerator.get(), "generated ID is required");
  }

  private static void requireRecruitingAccess(Actor actor) {
    Objects.requireNonNull(actor, "actor is required");
    if (actor.role() != WorkspaceRole.WORKSPACE_ADMIN && actor.role() != WorkspaceRole.RECRUITER) {
      throw new SecurityException("recruiting access is required");
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
