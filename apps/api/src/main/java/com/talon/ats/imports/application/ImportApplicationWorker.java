package com.talon.ats.imports.application;

import com.talon.ats.candidates.contract.CandidateImportAccess;
import com.talon.ats.files.application.ExternalFileFetchException;
import com.talon.ats.files.application.ResumeTransferService;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ImportApplicationWorker {

  private final ImportDraftRepository repository;
  private final CandidateImportAccess candidates;
  private final Clock clock;
  private final ResumeTransferService resumes;

  public ImportApplicationWorker(
      ImportDraftRepository repository,
      CandidateImportAccess candidates,
      ResumeTransferService resumes,
      Clock clock) {
    this.repository = Objects.requireNonNull(repository);
    this.candidates = Objects.requireNonNull(candidates);
    this.resumes = Objects.requireNonNull(resumes);
    this.clock = Objects.requireNonNull(clock);
  }

  public void process(ImportDraftService.Actor actor, UUID importId) {
    Objects.requireNonNull(actor);
    Objects.requireNonNull(importId);
    if (!repository.beginProcessing(actor.workspaceId(), importId, clock.instant())) return;
    ImportDraft draft =
        repository
            .find(actor.workspaceId(), importId)
            .orElseThrow(() -> new IllegalStateException("confirmed import was not found"));
    for (ImportProcessingRow processingRow :
        repository.findPendingRows(actor.workspaceId(), importId)) {
      try {
        NormalizedApplicationRow row = processingRow.application();
        CandidateImportAccess.Result result =
            candidates.createOrMatch(
                new CandidateImportAccess.Actor(actor.userId(), actor.workspaceId(), actor.role()),
                new CandidateImportAccess.Application(
                    draft.jobId(),
                    row.email(),
                    row.firstName(),
                    row.lastName(),
                    row.phone(),
                    row.location(),
                    row.currentTitle(),
                    row.currentCompany(),
                    row.skills(),
                    row.experienceMonths(),
                    row.source(),
                    row.applicationDate() == null
                        ? LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC)
                        : row.applicationDate(),
                    row.noticeDays(),
                    row.availabilityDate(),
                    money(row.currentCompensation()),
                    money(row.expectedCompensation()),
                    Map.of("resume_drive_url", row.resumeDriveUrl())));
        repository.markApplicationCreated(
            actor.workspaceId(),
            importId,
            processingRow.sourceRowNumber(),
            result.candidateId(),
            result.applicationId(),
            clock.instant());
        transferResume(actor, importId, processingRow, row, result.applicationId());
      } catch (RuntimeException failure) {
        repository.markRowFailed(
            actor.workspaceId(),
            importId,
            processingRow.sourceRowNumber(),
            "PERSISTENCE_FAILED",
            "Candidate application could not be created",
            clock.instant());
      }
    }
    repository.finishApplicationCreation(actor.workspaceId(), importId, clock.instant());
  }

  private void transferResume(
      ImportDraftService.Actor actor,
      UUID importId,
      ImportProcessingRow processingRow,
      NormalizedApplicationRow row,
      UUID applicationId) {
    UUID fileId = UUID.randomUUID();
    try {
      ResumeTransferService.TransferResult transferred =
          resumes.transfer(
              actor.workspaceId(), fileId, UUID.randomUUID(), URI.create(row.resumeDriveUrl()));
      candidates.attachResume(
          new CandidateImportAccess.Actor(actor.userId(), actor.workspaceId(), actor.role()),
          applicationId,
          new CandidateImportAccess.Resume(
              transferred.fileId(),
              "resume.pdf",
              transferred.objectKey().value(),
              "QUARANTINED",
              transferred.contentType(),
              transferred.sizeBytes()));
      repository.markResumeQuarantined(
          actor.workspaceId(), importId, processingRow.sourceRowNumber(), fileId, clock.instant());
    } catch (ExternalFileFetchException failure) {
      repository.markResumeFailed(
          actor.workspaceId(),
          importId,
          processingRow.sourceRowNumber(),
          failure.code(),
          "Public Drive PDF could not be transferred",
          clock.instant());
    } catch (IllegalArgumentException failure) {
      repository.markResumeFailed(
          actor.workspaceId(),
          importId,
          processingRow.sourceRowNumber(),
          "INVALID_SOURCE_URL",
          "Public Drive PDF link is invalid",
          clock.instant());
    } catch (RuntimeException failure) {
      repository.markResumeFailed(
          actor.workspaceId(),
          importId,
          processingRow.sourceRowNumber(),
          "RESUME_TRANSFER_FAILED",
          "Public Drive PDF could not be transferred",
          clock.instant());
    }
  }

  private static CandidateImportAccess.Money money(NormalizedMoney money) {
    return money == null
        ? null
        : new CandidateImportAccess.Money(money.currency(), money.minorUnits());
  }
}
