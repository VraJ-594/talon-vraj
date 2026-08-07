package com.talon.ats.jobs.application;

import com.talon.ats.identity.contract.WorkspaceRole;
import com.talon.ats.jobs.domain.Job;
import com.talon.ats.jobs.domain.JobStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class JobService {

  private final JobRepository repository;
  private final Supplier<UUID> idGenerator;
  private final Clock clock;

  public JobService(JobRepository repository, Supplier<UUID> idGenerator, Clock clock) {
    this.repository = Objects.requireNonNull(repository);
    this.idGenerator = Objects.requireNonNull(idGenerator);
    this.clock = Objects.requireNonNull(clock);
  }

  public List<Job> listImportTargets(Actor actor) {
    requireRecruitingAccess(actor);
    return List.copyOf(repository.findImportTargets(actor.workspaceId()));
  }

  public Job create(Actor actor, CreateJobCommand command) {
    requireRecruitingAccess(actor);
    Objects.requireNonNull(command, "command is required");
    Instant now = clock.instant();
    Job job =
        new Job(
            Objects.requireNonNull(idGenerator.get(), "generated ID is required"),
            actor.workspaceId(),
            command.title(),
            command.department(),
            command.location(),
            JobStatus.ACTIVE,
            0,
            now,
            now);
    return repository.save(job);
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
