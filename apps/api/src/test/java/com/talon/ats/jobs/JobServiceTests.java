package com.talon.ats.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.talon.ats.identity.contract.WorkspaceRole;
import com.talon.ats.jobs.application.CreateJobCommand;
import com.talon.ats.jobs.application.JobRepository;
import com.talon.ats.jobs.application.JobService;
import com.talon.ats.jobs.domain.Job;
import com.talon.ats.jobs.domain.JobStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JobServiceTests {

  private static final UUID WORKSPACE_ID = new UUID(0, 1);
  private static final UUID USER_ID = new UUID(0, 2);
  private static final UUID JOB_ID = new UUID(0, 3);
  private static final Instant NOW = Instant.parse("2026-08-07T18:00:00Z");

  @Test
  void listsOnlyImportTargetsFromTheAuthenticatedWorkspace() {
    RecordingJobRepository repository = new RecordingJobRepository();
    repository.jobs =
        List.of(
            new Job(
                JOB_ID,
                WORKSPACE_ID,
                "Backend Engineer",
                "Engineering",
                "Pune",
                JobStatus.ACTIVE,
                0,
                NOW,
                NOW));

    List<Job> result = service(repository).listImportTargets(actor(WorkspaceRole.RECRUITER));

    assertThat(repository.requestedWorkspaceId).isEqualTo(WORKSPACE_ID);
    assertThat(result).containsExactlyElementsOf(repository.jobs);
  }

  @Test
  void createsAnActiveJobOwnedByTheAuthenticatedWorkspace() {
    RecordingJobRepository repository = new RecordingJobRepository();

    Job created =
        service(repository)
            .create(
                actor(WorkspaceRole.WORKSPACE_ADMIN),
                new CreateJobCommand(" Backend Engineer ", " Engineering ", " Pune "));

    assertThat(created.id()).isEqualTo(JOB_ID);
    assertThat(created.workspaceId()).isEqualTo(WORKSPACE_ID);
    assertThat(created.title()).isEqualTo("Backend Engineer");
    assertThat(created.department()).isEqualTo("Engineering");
    assertThat(created.location()).isEqualTo("Pune");
    assertThat(created.status()).isEqualTo(JobStatus.ACTIVE);
    assertThat(repository.saved).isEqualTo(created);
  }

  @Test
  void rejectsBlankJobTitlesBeforePersistence() {
    RecordingJobRepository repository = new RecordingJobRepository();

    assertThatThrownBy(
            () ->
                service(repository)
                    .create(
                        actor(WorkspaceRole.RECRUITER),
                        new CreateJobCommand(" ", "Engineering", "Pune")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("title is required");
    assertThat(repository.saved).isNull();
  }

  private static JobService service(JobRepository repository) {
    return new JobService(repository, () -> JOB_ID, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static JobService.Actor actor(WorkspaceRole role) {
    return new JobService.Actor(USER_ID, WORKSPACE_ID, role);
  }

  private static final class RecordingJobRepository implements JobRepository {
    private UUID requestedWorkspaceId;
    private List<Job> jobs = List.of();
    private Job saved;

    @Override
    public List<Job> findImportTargets(UUID workspaceId) {
      requestedWorkspaceId = workspaceId;
      return jobs;
    }

    @Override
    public Job save(Job job) {
      saved = job;
      return job;
    }
  }
}
