package com.talon.ats.imports.application;

import com.talon.ats.files.application.PrivateObjectKey;
import com.talon.ats.imports.domain.CanonicalField;
import com.talon.ats.imports.domain.ImportStatus;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record ImportDraft(
    UUID id,
    UUID workspaceId,
    UUID jobId,
    UUID createdBy,
    String fileName,
    PrivateObjectKey sourceObjectKey,
    int rowCount,
    List<String> sourceColumns,
    Map<String, CanonicalField> suggestedMapping,
    ImportStatus status,
    long version,
    Instant createdAt,
    Instant updatedAt) {

  public ImportDraft {
    Objects.requireNonNull(id, "id is required");
    Objects.requireNonNull(workspaceId, "workspaceId is required");
    Objects.requireNonNull(jobId, "jobId is required");
    Objects.requireNonNull(createdBy, "createdBy is required");
    fileName = required(fileName, "fileName");
    Objects.requireNonNull(sourceObjectKey, "sourceObjectKey is required");
    if (rowCount < 1 || rowCount > 2_000) {
      throw new IllegalArgumentException("rowCount must be between 1 and 2000");
    }
    sourceColumns = List.copyOf(sourceColumns);
    if (sourceColumns.isEmpty()) {
      throw new IllegalArgumentException("sourceColumns must not be empty");
    }
    suggestedMapping =
        Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(suggestedMapping)));
    Objects.requireNonNull(status, "status is required");
    if (version < 0) {
      throw new IllegalArgumentException("version must not be negative");
    }
    Objects.requireNonNull(createdAt, "createdAt is required");
    Objects.requireNonNull(updatedAt, "updatedAt is required");
  }

  private static String required(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return value.trim();
  }
}
