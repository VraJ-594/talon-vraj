package com.talon.ats.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.talon.ats.candidates.contract.CandidateImportAccess;
import com.talon.ats.files.application.ExternalFileFetchException;
import com.talon.ats.files.application.PrivateObjectKey;
import com.talon.ats.files.application.ResumeTransferService;
import com.talon.ats.identity.contract.WorkspaceRole;
import com.talon.ats.imports.application.ImportApplicationWorker;
import com.talon.ats.imports.application.ImportDraft;
import com.talon.ats.imports.application.ImportDraftRepository;
import com.talon.ats.imports.application.ImportDraftService;
import com.talon.ats.imports.application.ImportProcessingRow;
import com.talon.ats.imports.application.NormalizedApplicationRow;
import com.talon.ats.imports.domain.ImportStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ImportApplicationWorkerTests {

  @Test
  void retainsPublicDriveResumeLinkWhenPrivateTransferFails() {
    UUID userId = UUID.randomUUID();
    UUID workspaceId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID importId = UUID.randomUUID();
    UUID candidateId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant now = Instant.parse("2026-08-08T10:00:00Z");
    String resumeUrl = "https://drive.google.com/file/d/public-resume/view";
    ImportDraftRepository repository = mock(ImportDraftRepository.class);
    CandidateImportAccess candidates = mock(CandidateImportAccess.class);
    ResumeTransferService resumes = mock(ResumeTransferService.class);
    ImportDraftService.Actor actor =
        new ImportDraftService.Actor(userId, workspaceId, WorkspaceRole.WORKSPACE_ADMIN);
    ImportDraft draft =
        new ImportDraft(
            importId,
            workspaceId,
            jobId,
            userId,
            "applications.csv",
            PrivateObjectKey.importSource(workspaceId, importId),
            1,
            List.of("email"),
            Map.of(),
            ImportStatus.CONFIRMED,
            0,
            now,
            now);
    NormalizedApplicationRow row =
        new NormalizedApplicationRow(
            2,
            "Test",
            "Candidate",
            "candidate@example.test",
            resumeUrl,
            null,
            "Pune",
            null,
            null,
            null,
            24,
            30,
            null,
            LocalDate.parse("2026-08-08"),
            "GOOGLE_FORM",
            null,
            null);

    given(repository.beginProcessing(workspaceId, importId, now)).willReturn(true);
    given(repository.find(workspaceId, importId)).willReturn(Optional.of(draft));
    given(repository.findPendingRows(workspaceId, importId))
        .willReturn(List.of(new ImportProcessingRow(2, row)));
    given(candidates.createOrMatch(any(), any()))
        .willReturn(new CandidateImportAccess.Result(candidateId, applicationId, true, true));
    given(resumes.transfer(any(), any(), any(), any()))
        .willThrow(
            new ExternalFileFetchException(
                "RESUME_FETCH_FAILED", "Public Drive PDF could not be transferred", true));

    new ImportApplicationWorker(repository, candidates, resumes, Clock.fixed(now, ZoneOffset.UTC))
        .process(actor, importId);

    ArgumentCaptor<CandidateImportAccess.Application> application =
        ArgumentCaptor.forClass(CandidateImportAccess.Application.class);
    verify(candidates).createOrMatch(any(), application.capture());
    assertThat(application.getValue().formAnswers()).containsEntry("resume_drive_url", resumeUrl);
    verify(repository)
        .markApplicationCreated(workspaceId, importId, 2, candidateId, applicationId, now);
    verify(repository)
        .markResumeFailed(
            workspaceId,
            importId,
            2,
            "RESUME_FETCH_FAILED",
            "Public Drive PDF could not be transferred",
            now);
    verify(repository).finishApplicationCreation(workspaceId, importId, now);
  }
}
