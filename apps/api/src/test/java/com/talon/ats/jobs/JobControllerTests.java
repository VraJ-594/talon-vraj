package com.talon.ats.jobs;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.talon.ats.identity.contract.WorkspaceRole;
import com.talon.ats.identity.infrastructure.security.SecurityConfiguration;
import com.talon.ats.jobs.api.JobController;
import com.talon.ats.jobs.application.CreateJobCommand;
import com.talon.ats.jobs.application.JobService;
import com.talon.ats.jobs.domain.Job;
import com.talon.ats.jobs.domain.JobStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = JobController.class, properties = "talon.security.enabled=true")
@Import(SecurityConfiguration.class)
class JobControllerTests {

  private static final UUID USER_ID = new UUID(0, 1);
  private static final UUID WORKSPACE_ID = new UUID(0, 2);
  private static final UUID JOB_ID = new UUID(0, 3);

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JobService jobService;
  @MockitoBean private JwtDecoder jwtDecoder;

  @Test
  void listsImportTargetsUsingOnlyTheVerifiedWorkspaceClaim() throws Exception {
    given(jobService.listImportTargets(any()))
        .willReturn(
            List.of(
                new Job(
                    JOB_ID,
                    WORKSPACE_ID,
                    "Backend Engineer",
                    "Engineering",
                    "Pune",
                    JobStatus.ACTIVE,
                    0,
                    Instant.parse("2026-08-07T18:00:00Z"),
                    Instant.parse("2026-08-07T18:00:00Z"))));

    mockMvc
        .perform(get("/api/v1/jobs").with(actorJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(JOB_ID.toString()))
        .andExpect(jsonPath("$[0].department").value("Engineering"))
        .andExpect(jsonPath("$[0].location").value("Pune"))
        .andExpect(jsonPath("$[0].status").value("OPEN"));

    ArgumentCaptor<JobService.Actor> actor = ArgumentCaptor.forClass(JobService.Actor.class);
    verify(jobService).listImportTargets(actor.capture());
    org.assertj.core.api.Assertions.assertThat(actor.getValue().workspaceId())
        .isEqualTo(WORKSPACE_ID);
  }

  @Test
  void createsAnImportTargetWithoutAcceptingAClientWorkspaceId() throws Exception {
    given(jobService.create(any(), any()))
        .willReturn(
            new Job(
                JOB_ID,
                WORKSPACE_ID,
                "Backend Engineer",
                "Engineering",
                "Pune",
                JobStatus.ACTIVE,
                0,
                Instant.parse("2026-08-07T18:00:00Z"),
                Instant.parse("2026-08-07T18:00:00Z")));

    mockMvc
        .perform(
            post("/api/v1/jobs")
                .with(actorJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title":"Backend Engineer","department":"Engineering","location":"Pune"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(JOB_ID.toString()));

    ArgumentCaptor<CreateJobCommand> command = ArgumentCaptor.forClass(CreateJobCommand.class);
    verify(jobService).create(any(), command.capture());
    org.assertj.core.api.Assertions.assertThat(command.getValue().title())
        .isEqualTo("Backend Engineer");
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor actorJwt() {
    return jwt()
        .jwt(
            token ->
                token
                    .subject(USER_ID.toString())
                    .claim("workspace_id", WORKSPACE_ID.toString())
                    .claim("role", WorkspaceRole.RECRUITER.name())
                    .claim("display_name", "Recruiter"));
  }
}
