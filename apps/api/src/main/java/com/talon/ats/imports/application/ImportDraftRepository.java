package com.talon.ats.imports.application;

import com.talon.ats.imports.domain.ColumnMapping;
import java.time.Instant;
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
}
