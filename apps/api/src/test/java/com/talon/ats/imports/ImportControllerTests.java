package com.talon.ats.imports;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.talon.ats.files.application.PrivateObjectKey;
import com.talon.ats.identity.contract.WorkspaceRole;
import com.talon.ats.identity.infrastructure.security.SecurityConfiguration;
import com.talon.ats.imports.api.ImportController;
import com.talon.ats.imports.api.ImportProblemHandler;
import com.talon.ats.imports.application.CsvParseException;
import com.talon.ats.imports.application.CsvPreviewIssue;
import com.talon.ats.imports.application.ImportDraft;
import com.talon.ats.imports.application.ImportDraftService;
import com.talon.ats.imports.application.ImportPreviewSnapshot;
import com.talon.ats.imports.application.ImportProblem;
import com.talon.ats.imports.domain.CanonicalField;
import com.talon.ats.imports.domain.ImportStatus;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ImportController.class, properties = "talon.security.enabled=true")
@Import({SecurityConfiguration.class, ImportProblemHandler.class})
class ImportControllerTests {

  private static final UUID USER_ID = new UUID(0, 1);
  private static final UUID WORKSPACE_ID = new UUID(0, 2);
  private static final UUID JOB_ID = new UUID(0, 3);
  private static final UUID IMPORT_ID = new UUID(0, 4);
  private static final Instant NOW = Instant.parse("2026-08-08T06:30:00Z");

  @Autowired private MockMvc mockMvc;
  @MockitoBean private ImportDraftService service;
  @MockitoBean private JwtDecoder jwtDecoder;

  @Test
  void rejectsAnonymousImportAccess() throws Exception {
    mockMvc.perform(get("/api/v1/imports/template")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/v1/imports/" + IMPORT_ID + "/preview"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void downloadsTheServerOwnedSyntheticTemplate() throws Exception {
    given(service.template(any()))
        .willReturn(
            "first_name,email\r\nSynthetic,candidate@example.com\r\n"
                .getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(get("/api/v1/imports/template").with(actorJwt()))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/csv"))
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"talon-candidate-import.csv\""))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Synthetic")));
  }

  @Test
  void uploadsMultipartCsvUsingOnlyVerifiedJwtOwnership() throws Exception {
    given(service.upload(any(), eq(JOB_ID), eq("applications.csv"), any())).willReturn(draft());
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "applications.csv",
            "text/csv",
            "first_name,last_name,email,resume_drive_url\n".getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart("/api/v1/imports")
                .file(file)
                .param("jobId", JOB_ID.toString())
                .with(actorJwt()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(IMPORT_ID.toString()))
        .andExpect(jsonPath("$.jobId").value(JOB_ID.toString()))
        .andExpect(jsonPath("$.status").value("UPLOADED"))
        .andExpect(jsonPath("$.suggestedMapping.first_name").value("first_name"))
        .andExpect(jsonPath("$.workspaceId").doesNotExist())
        .andExpect(jsonPath("$.sourceObjectKey").doesNotExist());

    ArgumentCaptor<ImportDraftService.Actor> actor =
        ArgumentCaptor.forClass(ImportDraftService.Actor.class);
    verify(service).upload(actor.capture(), eq(JOB_ID), eq("applications.csv"), any());
    org.assertj.core.api.Assertions.assertThat(actor.getValue().workspaceId())
        .isEqualTo(WORKSPACE_ID);
  }

  @Test
  void validatesLowercaseMappingAndReturnsSafeIssueCodes() throws Exception {
    given(service.validate(any(), eq(IMPORT_ID), anyMap(), eq(false)))
        .willReturn(
            new ImportPreviewSnapshot(
                IMPORT_ID,
                1,
                1,
                0,
                List.of(new CsvPreviewIssue(3, "INVALID", "INVALID_EMAIL", "email must be valid")),
                ImportStatus.PREVIEW_READY));

    mockMvc
        .perform(
            post("/api/v1/imports/" + IMPORT_ID + "/validate")
                .with(actorJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "mapping": {
                        "first_name": "first_name",
                        "last_name": "last_name",
                        "email": "email",
                        "resume_drive_url": "resume_drive_url"
                      },
                      "retainUnmapped": false
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validCount").value(1))
        .andExpect(jsonPath("$.invalidCount").value(1))
        .andExpect(jsonPath("$.duplicateCount").value(0))
        .andExpect(jsonPath("$.issues[0].rowNumber").value(3))
        .andExpect(jsonPath("$.issues[0].code").value("INVALID_EMAIL"));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, CanonicalField>> mapping = ArgumentCaptor.forClass(Map.class);
    verify(service).validate(any(), eq(IMPORT_ID), mapping.capture(), eq(false));
    org.assertj.core.api.Assertions.assertThat(mapping.getValue())
        .containsEntry("resume_drive_url", CanonicalField.RESUME_DRIVE_URL);
  }

  @Test
  void restoresADurablePreview() throws Exception {
    given(service.preview(any(), eq(IMPORT_ID)))
        .willReturn(
            new ImportPreviewSnapshot(IMPORT_ID, 2, 0, 0, List.of(), ImportStatus.PREVIEW_READY));

    mockMvc
        .perform(get("/api/v1/imports/" + IMPORT_ID + "/preview").with(actorJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validCount").value(2));
  }

  @Test
  void returnsStableSafeProblemsWithoutProviderOrCandidateDetails() throws Exception {
    given(service.upload(any(), eq(JOB_ID), eq("applications.csv"), any()))
        .willThrow(
            new ImportProblem(
                "IMPORT_STORAGE_FAILED",
                "The CSV could not be stored securely",
                new IllegalStateException("s3://private-bucket/imports/id candidate@example.com")));
    MockMultipartFile file =
        new MockMultipartFile("file", "applications.csv", "text/csv", new byte[] {1});

    mockMvc
        .perform(
            multipart("/api/v1/imports")
                .file(file)
                .param("jobId", JOB_ID.toString())
                .with(actorJwt()))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("IMPORT_STORAGE_FAILED"))
        .andExpect(jsonPath("$.detail").value("The CSV could not be stored securely"))
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("s3://"))))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("candidate@example.com"))));
  }

  @Test
  void mapsParserAndRoleFailuresToStableStatuses() throws Exception {
    given(service.preview(any(), eq(IMPORT_ID)))
        .willThrow(new CsvParseException("INVALID_CSV", "CSV could not be parsed"));
    mockMvc
        .perform(get("/api/v1/imports/" + IMPORT_ID + "/preview").with(actorJwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CSV"));

    given(service.preview(any(), eq(IMPORT_ID)))
        .willThrow(new SecurityException("recruiting access is required"));
    mockMvc
        .perform(get("/api/v1/imports/" + IMPORT_ID + "/preview").with(actorJwt()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("IMPORT_FORBIDDEN"));
  }

  private static ImportDraft draft() {
    Map<String, CanonicalField> mapping = new LinkedHashMap<>();
    mapping.put("first_name", CanonicalField.FIRST_NAME);
    mapping.put("last_name", CanonicalField.LAST_NAME);
    mapping.put("email", CanonicalField.EMAIL);
    mapping.put("resume_drive_url", CanonicalField.RESUME_DRIVE_URL);
    return new ImportDraft(
        IMPORT_ID,
        WORKSPACE_ID,
        JOB_ID,
        USER_ID,
        "applications.csv",
        PrivateObjectKey.importSource(WORKSPACE_ID, IMPORT_ID),
        1,
        List.copyOf(mapping.keySet()),
        mapping,
        ImportStatus.UPLOADED,
        0,
        NOW,
        NOW);
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
