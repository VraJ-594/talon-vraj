package com.talon.ats.imports.application;

import com.talon.ats.imports.domain.ColumnMapping;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportDraftRepository {

  ImportDraft create(ImportDraft draft);

  Optional<ImportDraft> find(UUID workspaceId, UUID importId);

  ImportPreviewSnapshot replacePreview(
      UUID workspaceId,
      UUID importId,
      ColumnMapping mapping,
      CsvParseResult result,
      Instant changedAt);

  Optional<ImportPreviewSnapshot> findPreview(UUID workspaceId, UUID importId);

  default ImportProgressSnapshot confirm(
      UUID workspaceId, UUID importId, UUID confirmationKey, Instant changedAt) {
    throw new UnsupportedOperationException("import confirmation is unavailable");
  }

  default boolean beginProcessing(UUID workspaceId, UUID importId, Instant changedAt) {
    throw new UnsupportedOperationException("import processing is unavailable");
  }

  default List<ImportProcessingRow> findPendingRows(UUID workspaceId, UUID importId) {
    throw new UnsupportedOperationException("import processing is unavailable");
  }

  default void markApplicationCreated(
      UUID workspaceId,
      UUID importId,
      int sourceRowNumber,
      UUID candidateId,
      UUID applicationId,
      Instant changedAt) {
    throw new UnsupportedOperationException("import processing is unavailable");
  }

  default void markRowFailed(
      UUID workspaceId,
      UUID importId,
      int sourceRowNumber,
      String code,
      String message,
      Instant changedAt) {
    throw new UnsupportedOperationException("import processing is unavailable");
  }

  default void markResumeQuarantined(
      UUID workspaceId, UUID importId, int sourceRowNumber, UUID resumeFileId, Instant changedAt) {
    throw new UnsupportedOperationException("resume processing is unavailable");
  }

  default void markResumeFailed(
      UUID workspaceId,
      UUID importId,
      int sourceRowNumber,
      String code,
      String message,
      Instant changedAt) {
    throw new UnsupportedOperationException("resume processing is unavailable");
  }

  default void finishApplicationCreation(UUID workspaceId, UUID importId, Instant changedAt) {
    throw new UnsupportedOperationException("import processing is unavailable");
  }

  default Optional<ImportProgressSnapshot> findProgress(UUID workspaceId, UUID importId) {
    throw new UnsupportedOperationException("import progress is unavailable");
  }
}
