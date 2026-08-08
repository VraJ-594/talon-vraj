package com.talon.ats.candidates;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.talon.ats.candidates.api.CandidateApplicationController;
import com.talon.ats.candidates.api.CandidateApplicationProblemHandler;
import com.talon.ats.candidates.application.AnnualCompensation;
import com.talon.ats.candidates.application.CandidateApplicationDetail;
import com.talon.ats.candidates.application.CandidateApplicationPage;
import com.talon.ats.candidates.application.CandidateApplicationQueryService;
import com.talon.ats.candidates.application.CandidateApplicationSummary;
import com.talon.ats.candidates.application.CandidateQueryException;
import com.talon.ats.candidates.application.CandidateResume;
import com.talon.ats.candidates.application.CandidateResumeStatus;
import com.talon.ats.files.application.ObjectStorage;
import com.talon.ats.files.application.PrivateObjectKey;
import com.talon.ats.identity.contract.WorkspaceRole;
import com.talon.ats.identity.infrastructure.security.SecurityConfiguration;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = CandidateApplicationController.class,
    properties = "talon.security.enabled=true")
@Import({SecurityConfiguration.class, CandidateApplicationProblemHandler.class})
class CandidateApplicationControllerTests {

  private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID WORKSPACE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
  private static final UUID CANDIDATE_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
  private static final UUID APPLICATION_ID =
      UUID.fromString("40000000-0000-0000-0000-000000000001");
  private static final UUID FILE_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
  private static final UUID VERSION_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");
  private static final byte[] PDF = "%PDF-1.7 synthetic".getBytes(StandardCharsets.US_ASCII);

  @Autowired private MockMvc mockMvc;
  @MockitoBean private CandidateApplicationQueryService service;
  @MockitoBean private ObjectStorage storage;
  @MockitoBean private JwtDecoder jwtDecoder;

  @Test
  void listsApplicationsUsingOnlyVerifiedJwtOwnership() throws Exception {
    given(service.list(any(), eq(null), eq(50)))
        .willReturn(new CandidateApplicationPage(List.of(summary()), "opaque-next"));

    mockMvc
        .perform(get("/api/v1/applications").with(actorJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].applicationId").value(APPLICATION_ID.toString()))
        .andExpect(jsonPath("$.items[0].candidateName").value("Asha Mehta"))
        .andExpect(jsonPath("$.items[0].resumeStatus").value("NO_RESUME"))
        .andExpect(jsonPath("$.nextCursor").value("opaque-next"))
        .andExpect(jsonPath("$.workspaceId").doesNotExist());

    ArgumentCaptor<CandidateApplicationQueryService.Actor> actor =
        ArgumentCaptor.forClass(CandidateApplicationQueryService.Actor.class);
    verify(service).list(actor.capture(), eq(null), eq(50));
    org.assertj.core.api.Assertions.assertThat(actor.getValue().workspaceId())
        .isEqualTo(WORKSPACE_ID);
  }

  @Test
  void returnsAFlatSafeApplicationDetail() throws Exception {
    given(service.detail(any(), eq(APPLICATION_ID))).willReturn(detail());

    mockMvc
        .perform(get("/api/v1/applications/" + APPLICATION_ID).with(actorJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.applicationId").value(APPLICATION_ID.toString()))
        .andExpect(jsonPath("$.candidateName").value("Asha Mehta"))
        .andExpect(jsonPath("$.email").value("search-demo@example.test"))
        .andExpect(jsonPath("$.additionalAnswers[0].question").value("Preferred shift"))
        .andExpect(jsonPath("$.additionalAnswers[0].answer").value("Day"))
        .andExpect(jsonPath("$.summary").doesNotExist())
        .andExpect(jsonPath("$.objectKey").doesNotExist());
  }

  @Test
  void streamsALocalCleanResumeWithoutExposingTheObjectKey() throws Exception {
    CandidateResume resume = resume();
    given(service.resume(any(), eq(APPLICATION_ID))).willReturn(resume);
    given(storage.presignCleanDownload(any(), any(Duration.class), eq("synthetic-resume.pdf")))
        .willReturn(Optional.empty());
    given(storage.open(resume.objectKey())).willReturn(new ByteArrayInputStream(PDF));

    mockMvc
        .perform(
            get("/api/v1/applications/" + APPLICATION_ID + "/resume-download").with(actorJwt()))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"synthetic-resume.pdf\""))
        .andExpect(content().bytes(PDF));
  }

  @Test
  void redirectsToAShortLivedProviderUrlWhenAvailable() throws Exception {
    CandidateResume resume = resume();
    URI signed = URI.create("https://objects.example.test/signed-resume");
    given(service.resume(any(), eq(APPLICATION_ID))).willReturn(resume);
    given(storage.presignCleanDownload(any(), eq(Duration.ofMinutes(5)), any()))
        .willReturn(Optional.of(signed));

    mockMvc
        .perform(
            get("/api/v1/applications/" + APPLICATION_ID + "/resume-download").with(actorJwt()))
        .andExpect(status().isSeeOther())
        .andExpect(header().string(HttpHeaders.LOCATION, signed.toString()));
  }

  @Test
  void mapsCandidateFailuresToStableSafeProblems() throws Exception {
    given(service.detail(any(), eq(APPLICATION_ID)))
        .willThrow(
            new CandidateQueryException(
                "CANDIDATE_APPLICATION_NOT_FOUND", "Candidate application was not found"));

    mockMvc
        .perform(get("/api/v1/applications/" + APPLICATION_ID).with(actorJwt()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("CANDIDATE_APPLICATION_NOT_FOUND"))
        .andExpect(jsonPath("$.detail").value("Candidate application was not found"));
  }

  private static CandidateApplicationSummary summary() {
    return new CandidateApplicationSummary(
        APPLICATION_ID,
        CANDIDATE_ID,
        "Asha Mehta",
        "Senior Platform Engineer",
        "SCREENING",
        "Pune",
        96,
        "Finch Labs",
        "Senior Java Engineer",
        List.of("Java", "Spring Boot", "PostgreSQL"),
        new AnnualCompensation("INR", 320_000_000L),
        new AnnualCompensation("INR", 380_000_000L),
        30,
        LocalDate.parse("2026-08-06"),
        CandidateResumeStatus.NO_RESUME);
  }

  private static CandidateApplicationDetail detail() {
    return new CandidateApplicationDetail(
        summary(),
        "search-demo@example.test",
        "+91 ••••••1234",
        "SEARCH_DEMO",
        LocalDate.parse("2026-09-15"),
        Map.of("Preferred shift", "Day"),
        "",
        false);
  }

  private static CandidateResume resume() {
    return new CandidateResume(
        WORKSPACE_ID,
        "synthetic-resume.pdf",
        "application/pdf",
        PrivateObjectKey.cleanResume(WORKSPACE_ID, FILE_ID, VERSION_ID));
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
