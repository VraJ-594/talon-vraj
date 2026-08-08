package com.talon.ats.candidates.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talon.ats.candidates.application.AnnualCompensation;
import com.talon.ats.candidates.application.CandidateApplicationCursor;
import com.talon.ats.candidates.application.CandidateApplicationDetail;
import com.talon.ats.candidates.application.CandidateApplicationQueryStore;
import com.talon.ats.candidates.application.CandidateApplicationSlice;
import com.talon.ats.candidates.application.CandidateApplicationSummary;
import com.talon.ats.candidates.application.CandidateResume;
import com.talon.ats.candidates.application.CandidateResumeStatus;
import com.talon.ats.files.application.PrivateObjectKey;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionOperations;

public final class JdbcCandidateApplicationQueryStore implements CandidateApplicationQueryStore {

  private static final String PROJECTION =
      """
      SELECT a.id AS application_id, c.id AS candidate_id,
             concat_ws(' ', c.first_name, c.last_name) AS candidate_name,
             j.title AS job_title, a.stage, coalesce(c.location, '') AS location,
             coalesce(c.experience_months, 0) AS experience_months,
             coalesce(c.current_company, '') AS current_company,
             coalesce(c.current_title, '') AS current_title,
             coalesce(c.skills_text, '') AS skills_text,
             a.current_ctc_currency, a.current_ctc_minor,
             a.expected_ctc_currency, a.expected_ctc_minor,
             coalesce(a.notice_days, 0) AS notice_days, a.applied_at,
             coalesce(f.status, 'NO_RESUME') AS resume_status,
             c.email, c.phone, coalesce(a.source, '') AS source,
             a.available_from, a.form_answers::text AS form_answers,
             coalesce(f.file_name, '') AS resume_file_name
      FROM application a
      JOIN candidate c ON c.workspace_id = a.workspace_id AND c.id = a.candidate_id
      JOIN job j ON j.workspace_id = a.workspace_id AND j.id = a.job_id
      LEFT JOIN candidate_file f
        ON f.workspace_id = a.workspace_id AND f.application_id = a.id
      """;

  private final JdbcTemplate jdbc;
  private final TransactionOperations transactions;
  private final ObjectMapper objectMapper;

  public JdbcCandidateApplicationQueryStore(
      JdbcTemplate jdbc, TransactionOperations transactions, ObjectMapper objectMapper) {
    this.jdbc = Objects.requireNonNull(jdbc, "jdbc is required");
    this.transactions = Objects.requireNonNull(transactions, "transactions are required");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
  }

  @Override
  public CandidateApplicationSlice list(
      UUID workspaceId, CandidateApplicationCursor cursor, int limit) {
    CandidateApplicationSlice result =
        transactions.execute(
            ignored -> {
              setTenantContext(workspaceId);
              List<CandidateApplicationSummary> rows = listRows(workspaceId, cursor, limit + 1);
              boolean hasMore = rows.size() > limit;
              List<CandidateApplicationSummary> page =
                  hasMore ? List.copyOf(rows.subList(0, limit)) : List.copyOf(rows);
              CandidateApplicationCursor next =
                  hasMore && !page.isEmpty()
                      ? new CandidateApplicationCursor(
                          page.getLast().applicationDate(), page.getLast().applicationId())
                      : null;
              return new CandidateApplicationSlice(page, next);
            });
    return Objects.requireNonNull(result, "candidate list transaction result is required");
  }

  @Override
  public Optional<CandidateApplicationDetail> findDetail(UUID workspaceId, UUID applicationId) {
    Optional<CandidateApplicationDetail> result =
        transactions.execute(
            ignored -> {
              setTenantContext(workspaceId);
              return jdbc
                  .query(
                      PROJECTION + " WHERE a.workspace_id = ? AND a.id = ? ORDER BY a.id LIMIT 1",
                      (resultSet, rowNumber) -> mapDetail(resultSet),
                      workspaceId,
                      applicationId)
                  .stream()
                  .findFirst();
            });
    return Objects.requireNonNull(result, "candidate detail transaction result is required");
  }

  @Override
  public Optional<CandidateResume> findCleanResume(UUID workspaceId, UUID applicationId) {
    Optional<CandidateResume> result =
        transactions.execute(
            ignored -> {
              setTenantContext(workspaceId);
              return jdbc
                  .query(
                      """
                      SELECT f.file_name, f.content_type, f.object_key
                      FROM candidate_file f
                      WHERE f.workspace_id = ? AND f.application_id = ? AND f.status = 'CLEAN'
                      LIMIT 1
                      """,
                      (resultSet, rowNumber) ->
                          new CandidateResume(
                              workspaceId,
                              resultSet.getString("file_name"),
                              resultSet.getString("content_type"),
                              PrivateObjectKey.parse(resultSet.getString("object_key"))),
                      workspaceId,
                      applicationId)
                  .stream()
                  .findFirst();
            });
    return Objects.requireNonNull(result, "candidate resume transaction result is required");
  }

  private List<CandidateApplicationSummary> listRows(
      UUID workspaceId, CandidateApplicationCursor cursor, int rowLimit) {
    if (cursor == null) {
      return jdbc.query(
          PROJECTION
              + " WHERE a.workspace_id = ?"
              + " ORDER BY a.applied_at DESC, a.id DESC LIMIT ?",
          (resultSet, rowNumber) -> mapSummary(resultSet),
          workspaceId,
          rowLimit);
    }
    return jdbc.query(
        PROJECTION
            + " WHERE a.workspace_id = ?"
            + " AND (a.applied_at < ? OR (a.applied_at = ? AND a.id < ?))"
            + " ORDER BY a.applied_at DESC, a.id DESC LIMIT ?",
        (resultSet, rowNumber) -> mapSummary(resultSet),
        workspaceId,
        cursor.appliedAt(),
        cursor.appliedAt(),
        cursor.applicationId(),
        rowLimit);
  }

  private CandidateApplicationDetail mapDetail(ResultSet resultSet) throws SQLException {
    CandidateApplicationSummary summary = mapSummary(resultSet);
    return new CandidateApplicationDetail(
        summary,
        resultSet.getString("email"),
        maskPhone(resultSet.getString("phone")),
        resultSet.getString("source"),
        resultSet.getObject("available_from", LocalDate.class),
        answers(resultSet.getString("form_answers")),
        resultSet.getString("resume_file_name"),
        summary.resumeStatus() == CandidateResumeStatus.CLEAN);
  }

  private static CandidateApplicationSummary mapSummary(ResultSet resultSet) throws SQLException {
    return new CandidateApplicationSummary(
        resultSet.getObject("application_id", UUID.class),
        resultSet.getObject("candidate_id", UUID.class),
        resultSet.getString("candidate_name"),
        resultSet.getString("job_title"),
        resultSet.getString("stage"),
        resultSet.getString("location"),
        resultSet.getInt("experience_months"),
        resultSet.getString("current_company"),
        resultSet.getString("current_title"),
        skills(resultSet.getString("skills_text")),
        compensation(
            resultSet.getString("current_ctc_currency"),
            resultSet.getObject("current_ctc_minor", Long.class)),
        compensation(
            resultSet.getString("expected_ctc_currency"),
            resultSet.getObject("expected_ctc_minor", Long.class)),
        resultSet.getInt("notice_days"),
        resultSet.getObject("applied_at", LocalDate.class),
        CandidateResumeStatus.valueOf(resultSet.getString("resume_status")));
  }

  private Map<String, String> answers(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      JsonNode root = objectMapper.readTree(json);
      Map<String, String> answers = new LinkedHashMap<>();
      root.fields()
          .forEachRemaining(
              entry -> {
                if (entry.getValue().isValueNode()) {
                  answers.put(entry.getKey(), entry.getValue().asText());
                }
              });
      return Map.copyOf(answers);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("candidate form answers are invalid", exception);
    }
  }

  private static List<String> skills(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(skill -> !skill.isEmpty())
        .distinct()
        .toList();
  }

  private static AnnualCompensation compensation(String currency, Long minorUnits) {
    return currency == null || minorUnits == null
        ? null
        : new AnnualCompensation(currency, minorUnits);
  }

  private static String maskPhone(String phone) {
    if (phone == null || phone.isBlank()) {
      return "Not provided";
    }
    String digits = phone.replaceAll("\\D", "");
    if (digits.length() <= 4) {
      return "••••";
    }
    return "••••••" + digits.substring(digits.length() - 4);
  }

  private void setTenantContext(UUID workspaceId) {
    jdbc.queryForObject(
        "SELECT set_config('app.current_workspace_id', ?, true)",
        String.class,
        workspaceId.toString());
    jdbc.execute("SET LOCAL ROLE talon_app");
  }
}
