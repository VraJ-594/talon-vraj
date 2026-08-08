package com.talon.ats.imports.application;

import com.talon.ats.files.application.ObjectStorage;
import com.talon.ats.files.application.PrivateObjectKey;
import com.talon.ats.identity.contract.WorkspaceRole;
import com.talon.ats.imports.domain.CanonicalField;
import com.talon.ats.imports.domain.ColumnMapping;
import com.talon.ats.imports.domain.ImportStatus;
import com.talon.ats.jobs.contract.ImportTargetAccess;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class ImportDraftService {

  private static final long MAXIMUM_CSV_BYTES = 10L * 1024 * 1024;

  private final CsvApplicationParser parser;
  private final StrictTalonImportTemplate template;
  private final ImportDraftRepository repository;
  private final ObjectStorage storage;
  private final ImportTargetAccess importTargets;
  private final Supplier<UUID> idGenerator;
  private final Clock clock;

  public ImportDraftService(
      CsvApplicationParser parser,
      StrictTalonImportTemplate template,
      ImportDraftRepository repository,
      ObjectStorage storage,
      ImportTargetAccess importTargets,
      Supplier<UUID> idGenerator,
      Clock clock) {
    this.parser = Objects.requireNonNull(parser);
    this.template = Objects.requireNonNull(template);
    this.repository = Objects.requireNonNull(repository);
    this.storage = Objects.requireNonNull(storage);
    this.importTargets = Objects.requireNonNull(importTargets);
    this.idGenerator = Objects.requireNonNull(idGenerator);
    this.clock = Objects.requireNonNull(clock);
  }

  public ImportDraft upload(
      Actor actor, UUID jobId, String fileName, ReopenableUpload uploadedCsv) {
    requireRecruitingAccess(actor);
    Objects.requireNonNull(jobId, "jobId is required");
    Objects.requireNonNull(uploadedCsv, "uploadedCsv is required");

    CsvInspection inspection;
    try (InputStream input = open(uploadedCsv)) {
      inspection = parser.inspect(input);
    } catch (IOException exception) {
      throw new ImportProblem("INVALID_CSV", "CSV could not be read", exception);
    }
    Map<String, CanonicalField> recognized = template.recognize(inspection);
    if (!importTargets.isImportable(actor.workspaceId(), jobId)) {
      throw new ImportProblem("JOB_NOT_IMPORTABLE", "The selected job is not importable");
    }

    UUID importId = Objects.requireNonNull(idGenerator.get(), "generated ID is required");
    PrivateObjectKey key = PrivateObjectKey.importSource(actor.workspaceId(), importId);
    try (InputStream input = open(uploadedCsv)) {
      storage.put(key, input, MAXIMUM_CSV_BYTES);
    } catch (IOException | RuntimeException exception) {
      throw new ImportProblem(
          "IMPORT_STORAGE_FAILED", "The CSV could not be stored securely", exception);
    }

    Instant now = clock.instant();
    ImportDraft draft =
        new ImportDraft(
            importId,
            actor.workspaceId(),
            jobId,
            actor.userId(),
            displayFileName(fileName),
            key,
            inspection.rowCount(),
            inspection.sourceColumns(),
            recognized,
            ImportStatus.UPLOADED,
            0,
            now,
            now);
    try {
      return repository.create(draft);
    } catch (RuntimeException persistenceFailure) {
      try {
        storage.delete(key);
      } catch (RuntimeException cleanupFailure) {
        persistenceFailure.addSuppressed(cleanupFailure);
      }
      throw new ImportProblem(
          "IMPORT_STORAGE_FAILED", "The import draft could not be saved", persistenceFailure);
    }
  }

  public ImportPreviewSnapshot validate(
      Actor actor,
      UUID importId,
      Map<String, CanonicalField> requestedMapping,
      boolean retainUnmapped) {
    requireRecruitingAccess(actor);
    Objects.requireNonNull(importId, "importId is required");
    ImportDraft draft =
        repository
            .find(actor.workspaceId(), importId)
            .filter(candidate -> candidate.workspaceId().equals(actor.workspaceId()))
            .orElseThrow(ImportDraftService::notFound);
    ColumnMapping mapping =
        template.requireExactMapping(
            draft.sourceColumns(), Objects.requireNonNull(requestedMapping), retainUnmapped);
    CsvParseResult result;
    try (InputStream input = storage.open(draft.sourceObjectKey())) {
      result = parser.parse(input, mapping, false);
    } catch (IOException | RuntimeException exception) {
      if (exception instanceof CsvParseException csvProblem) {
        throw csvProblem;
      }
      throw new ImportProblem(
          "IMPORT_STORAGE_FAILED", "The stored CSV could not be read", exception);
    }
    try {
      return repository.replacePreview(
          actor.workspaceId(), importId, mapping, result, clock.instant());
    } catch (NoSuchElementException exception) {
      throw notFound();
    } catch (RuntimeException exception) {
      throw new ImportProblem(
          "IMPORT_STORAGE_FAILED", "The validation preview could not be saved", exception);
    }
  }

  public ImportPreviewSnapshot preview(Actor actor, UUID importId) {
    requireRecruitingAccess(actor);
    Objects.requireNonNull(importId, "importId is required");
    return repository
        .findPreview(actor.workspaceId(), importId)
        .orElseThrow(ImportDraftService::notFound);
  }

  public byte[] template(Actor actor) {
    requireRecruitingAccess(actor);
    return template.downloadBytes();
  }

  private static InputStream open(ReopenableUpload upload) {
    try {
      return Objects.requireNonNull(upload.open(), "upload stream is required");
    } catch (IOException exception) {
      throw new ImportProblem("INVALID_CSV", "CSV could not be read", exception);
    }
  }

  private static String displayFileName(String fileName) {
    String normalized = fileName == null ? "" : fileName.replace('\\', '/');
    normalized =
        normalized.substring(normalized.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
    if (normalized.isEmpty()) {
      return "applications.csv";
    }
    return normalized.substring(0, Math.min(normalized.length(), 255));
  }

  private static ImportProblem notFound() {
    return new ImportProblem("IMPORT_NOT_FOUND", "The import could not be found");
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

  @FunctionalInterface
  public interface ReopenableUpload {
    InputStream open() throws IOException;
  }
}
