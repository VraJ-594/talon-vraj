package com.talon.ats.jobs.api;

import com.talon.ats.identity.contract.WorkspaceRole;
import com.talon.ats.jobs.application.CreateJobCommand;
import com.talon.ats.jobs.application.JobService;
import com.talon.ats.jobs.domain.Job;
import com.talon.ats.jobs.domain.JobStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs")
@ConditionalOnProperty(name = "talon.security.enabled", havingValue = "true")
public class JobController {

  private final JobService jobService;

  public JobController(JobService jobService) {
    this.jobService = jobService;
  }

  @GetMapping
  List<JobResponse> listImportTargets(@AuthenticationPrincipal Jwt jwt) {
    return jobService.listImportTargets(actor(jwt)).stream().map(JobResponse::from).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  JobResponse create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateJobRequest request) {
    return JobResponse.from(
        jobService.create(
            actor(jwt),
            new CreateJobCommand(request.title(), request.department(), request.location())));
  }

  private static JobService.Actor actor(Jwt jwt) {
    return new JobService.Actor(
        UUID.fromString(jwt.getSubject()),
        UUID.fromString(jwt.getClaimAsString("workspace_id")),
        WorkspaceRole.valueOf(jwt.getClaimAsString("role")));
  }

  record CreateJobRequest(
      @NotBlank String title, @NotBlank String department, @NotBlank String location) {}

  record JobResponse(UUID id, String title, String department, String location, String status) {

    static JobResponse from(Job job) {
      return new JobResponse(
          job.id(), job.title(), job.department(), job.location(), apiStatus(job.status()));
    }

    private static String apiStatus(JobStatus status) {
      return status == JobStatus.ACTIVE ? "OPEN" : status.name();
    }
  }
}
