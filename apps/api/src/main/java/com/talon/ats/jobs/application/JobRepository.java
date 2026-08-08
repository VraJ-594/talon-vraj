package com.talon.ats.jobs.application;

import com.talon.ats.jobs.domain.Job;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository {

  List<Job> findImportTargets(UUID workspaceId);

  Optional<Job> findImportTarget(UUID workspaceId, UUID jobId);

  Job save(Job job);
}
