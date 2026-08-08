package com.talon.ats.imports.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talon.ats.files.application.PrivateObjectKey;
import com.talon.ats.imports.application.CsvParseResult;
import com.talon.ats.imports.application.CsvPreviewIssue;
import com.talon.ats.imports.application.ImportDraft;
import com.talon.ats.imports.application.ImportDraftRepository;
import com.talon.ats.imports.application.ImportPreviewSnapshot;
import com.talon.ats.imports.application.ParsedApplicationRow;
import com.talon.ats.imports.domain.CanonicalField;
import com.talon.ats.imports.domain.ColumnMapping;
import com.talon.ats.imports.domain.ImportStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionOperations;

public final class JdbcImportDraftRepository implements ImportDraftRepository {

  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
  private static final TypeReference<Map<String, CanonicalField>> MAPPING =
      new TypeReference<>() {};
  private static final TypeReference<List<CsvPreviewIssue>> ISSUES = new TypeReference<>() {};

  private final JdbcTemplate jdbc;
  private final TransactionOperations transactions;
  private final ObjectMapper objectMapper;

  public JdbcImportDraftRepository(
      JdbcTemplate jdbc, TransactionOperations transactions, ObjectMapper objectMapper) {
    this.jdbc = Objects.requireNonNull(jdbc);
    this.transactions = Objects.requireNonNull(transactions);
    this.objectMapper = Objects.requireNonNull(objectMapper);
  }

  @Override
  public ImportDraft create(ImportDraft draft) {
    Objects.requireNonNull(draft, "draft is required");
    transactions.executeWithoutResult(
        status -> {
          setTenantContext(draft.workspaceId());
          jdbc.update(
              """
              INSERT INTO candidate_import(
                  id, workspace_id, job_id, created_by, source_object_key, file_name,
                  row_count, source_columns, suggested_mapping, status, version,
                  created_at, updated_at)
              VALUES (?,?,?,?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),?,?,?,?)
              """,
              draft.id(),
              draft.workspaceId(),
              draft.jobId(),
              draft.createdBy(),
              draft.sourceObjectKey().value(),
              draft.fileName(),
              draft.rowCount(),
              json(draft.sourceColumns()),
              json(draft.suggestedMapping()),
              draft.status().name(),
              draft.version(),
              Timestamp.from(draft.createdAt()),
              Timestamp.from(draft.updatedAt()));
        });
    return draft;
  }

  @Override
  public Optional<ImportDraft> find(UUID workspaceId, UUID importId) {
    requireIds(workspaceId, importId);
    Optional<ImportDraft> result =
        transactions.execute(
            status -> {
              setTenantContext(workspaceId);
              return jdbc
                  .query(
                      """
                      SELECT id, workspace_id, job_id, created_by, source_object_key, file_name,
                             row_count, source_columns::text, suggested_mapping::text, status,
                             version, created_at, updated_at
                      FROM candidate_import
                      WHERE workspace_id = ? AND id = ?
                      """,
                      this::mapDraft,
                      workspaceId,
                      importId)
                  .stream()
                  .findFirst();
            });
    return result == null ? Optional.empty() : result;
  }

  @Override
  public ImportPreviewSnapshot replacePreview(
      UUID workspaceId,
      UUID importId,
      ColumnMapping mapping,
      CsvParseResult result,
      Instant changedAt) {
    requireIds(workspaceId, importId);
    Objects.requireNonNull(mapping, "mapping is required");
    Objects.requireNonNull(result, "result is required");
    Objects.requireNonNull(changedAt, "changedAt is required");
    ImportPreviewSnapshot snapshot =
        transactions.execute(
            status -> {
              setTenantContext(workspaceId);
              Integer rowCount =
                  jdbc
                      .query(
                          """
                          SELECT row_count
                          FROM candidate_import
                          WHERE workspace_id = ? AND id = ?
                            AND status IN ('UPLOADED','MAPPED','VALIDATING','PREVIEW_READY')
                          FOR UPDATE
                          """,
                          (rows, number) -> rows.getInt("row_count"),
                          workspaceId,
                          importId)
                      .stream()
                      .findFirst()
                      .orElseThrow(() -> new NoSuchElementException("import draft was not found"));
              requireCompleteResult(rowCount, result);
              jdbc.update(
                  "DELETE FROM candidate_import_row WHERE workspace_id = ? AND import_id = ?",
                  workspaceId,
                  importId);
              insertValidRows(workspaceId, importId, result.validRows(), changedAt);
              insertIssueRows(workspaceId, importId, result.issues(), changedAt);
              int updated =
                  jdbc.update(
                      """
                      UPDATE candidate_import
                      SET mapping = CAST(? AS jsonb), status = 'PREVIEW_READY',
                          valid_count = ?, invalid_count = ?, duplicate_count = ?,
                          version = version + 1, updated_at = ?
                      WHERE workspace_id = ? AND id = ?
                      """,
                      json(mapping.assignments()),
                      result.validCount(),
                      result.invalidCount(),
                      result.duplicateCount(),
                      Timestamp.from(changedAt),
                      workspaceId,
                      importId);
              if (updated != 1) {
                throw new IllegalStateException("import preview update did not affect one draft");
              }
              return snapshot(importId, result);
            });
    return Objects.requireNonNull(snapshot, "preview transaction returned no result");
  }

  @Override
  public Optional<ImportPreviewSnapshot> findPreview(UUID workspaceId, UUID importId) {
    requireIds(workspaceId, importId);
    Optional<ImportPreviewSnapshot> result =
        transactions.execute(
            status -> {
              setTenantContext(workspaceId);
              Optional<PreviewCounts> counts =
                  jdbc
                      .query(
                          """
                          SELECT valid_count, invalid_count, duplicate_count, status
                          FROM candidate_import
                          WHERE workspace_id = ? AND id = ? AND status = 'PREVIEW_READY'
                          """,
                          (rows, number) ->
                              new PreviewCounts(
                                  rows.getInt("valid_count"),
                                  rows.getInt("invalid_count"),
                                  rows.getInt("duplicate_count"),
                                  ImportStatus.valueOf(rows.getString("status"))),
                          workspaceId,
                          importId)
                      .stream()
                      .findFirst();
              if (counts.isEmpty()) {
                return Optional.empty();
              }
              List<CsvPreviewIssue> issues =
                  jdbc
                      .query(
                          """
                          SELECT issues::text
                          FROM candidate_import_row
                          WHERE workspace_id = ? AND import_id = ? AND status <> 'VALID'
                          ORDER BY source_row_number
                          """,
                          (rows, number) -> read(rows.getString("issues"), ISSUES),
                          workspaceId,
                          importId)
                      .stream()
                      .flatMap(List::stream)
                      .toList();
              PreviewCounts value = counts.orElseThrow();
              return Optional.of(
                  new ImportPreviewSnapshot(
                      importId,
                      value.validCount(),
                      value.invalidCount(),
                      value.duplicateCount(),
                      issues,
                      value.status()));
            });
    return result == null ? Optional.empty() : result;
  }

  private void insertValidRows(
      UUID workspaceId, UUID importId, List<ParsedApplicationRow> rows, Instant changedAt) {
    for (ParsedApplicationRow row : rows) {
      jdbc.update(
          """
          INSERT INTO candidate_import_row(
              workspace_id, import_id, source_row_number, status,
              normalized_payload, issues, created_at, updated_at)
          VALUES (?, ?, ?, 'VALID', CAST(? AS jsonb), '[]'::jsonb, ?, ?)
          """,
          workspaceId,
          importId,
          row.row().rowNumber(),
          json(row.row()),
          Timestamp.from(changedAt),
          Timestamp.from(changedAt));
    }
  }

  private void insertIssueRows(
      UUID workspaceId, UUID importId, List<CsvPreviewIssue> issues, Instant changedAt) {
    Map<Integer, List<CsvPreviewIssue>> byRow = new LinkedHashMap<>();
    for (CsvPreviewIssue issue : issues) {
      byRow.computeIfAbsent(issue.rowNumber(), ignored -> new ArrayList<>()).add(issue);
    }
    byRow.forEach(
        (rowNumber, rowIssues) -> {
          String rowStatus =
              rowIssues.stream().allMatch(issue -> "DUPLICATE".equals(issue.kind()))
                  ? "DUPLICATE"
                  : "INVALID";
          jdbc.update(
              """
              INSERT INTO candidate_import_row(
                  workspace_id, import_id, source_row_number, status,
                  normalized_payload, issues, created_at, updated_at)
              VALUES (?, ?, ?, ?, NULL, CAST(? AS jsonb), ?, ?)
              """,
              workspaceId,
              importId,
              rowNumber,
              rowStatus,
              json(rowIssues),
              Timestamp.from(changedAt),
              Timestamp.from(changedAt));
        });
  }

  private ImportDraft mapDraft(ResultSet rows, int rowNumber) throws SQLException {
    return new ImportDraft(
        rows.getObject("id", UUID.class),
        rows.getObject("workspace_id", UUID.class),
        rows.getObject("job_id", UUID.class),
        rows.getObject("created_by", UUID.class),
        rows.getString("file_name"),
        PrivateObjectKey.parse(rows.getString("source_object_key")),
        rows.getInt("row_count"),
        read(rows.getString("source_columns"), STRING_LIST),
        read(rows.getString("suggested_mapping"), MAPPING),
        ImportStatus.valueOf(rows.getString("status")),
        rows.getLong("version"),
        rows.getObject("created_at", OffsetDateTime.class).toInstant(),
        rows.getObject("updated_at", OffsetDateTime.class).toInstant());
  }

  private void setTenantContext(UUID workspaceId) {
    jdbc.queryForObject(
        "SELECT set_config('app.current_workspace_id', ?, true)",
        String.class,
        workspaceId.toString());
    jdbc.execute("SET LOCAL ROLE talon_app");
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("import persistence JSON encoding failed", exception);
    }
  }

  private <T> T read(String value, TypeReference<T> type) throws SQLException {
    try {
      return objectMapper.readValue(value, type);
    } catch (JsonProcessingException exception) {
      throw new SQLException("import persistence JSON decoding failed", exception);
    }
  }

  private static void requireCompleteResult(int rowCount, CsvParseResult result) {
    if (result.validCount() + result.invalidCount() + result.duplicateCount() != rowCount) {
      throw new IllegalArgumentException("preview counts must equal the import row count");
    }
    long issueRows = result.issues().stream().map(CsvPreviewIssue::rowNumber).distinct().count();
    if (issueRows != (long) result.invalidCount() + result.duplicateCount()) {
      throw new IllegalArgumentException("preview issues must identify every rejected row");
    }
  }

  private static ImportPreviewSnapshot snapshot(UUID importId, CsvParseResult result) {
    return new ImportPreviewSnapshot(
        importId,
        result.validCount(),
        result.invalidCount(),
        result.duplicateCount(),
        result.issues(),
        ImportStatus.PREVIEW_READY);
  }

  private static void requireIds(UUID workspaceId, UUID importId) {
    Objects.requireNonNull(workspaceId, "workspaceId is required");
    Objects.requireNonNull(importId, "importId is required");
  }

  private record PreviewCounts(
      int validCount, int invalidCount, int duplicateCount, ImportStatus status) {}
}
