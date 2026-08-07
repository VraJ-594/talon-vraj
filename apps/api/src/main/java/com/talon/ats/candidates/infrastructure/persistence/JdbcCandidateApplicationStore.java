package com.talon.ats.candidates.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talon.ats.candidates.application.AnnualCompensation;
import com.talon.ats.candidates.application.ApplicationData;
import com.talon.ats.candidates.application.CandidateApplicationCommand;
import com.talon.ats.candidates.application.CandidateApplicationResult;
import com.talon.ats.candidates.application.CandidateApplicationStore;
import com.talon.ats.candidates.application.CandidateData;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionOperations;

public final class JdbcCandidateApplicationStore implements CandidateApplicationStore {

  private final JdbcTemplate jdbc;
  private final TransactionOperations transactions;
  private final ObjectMapper objectMapper;

  public JdbcCandidateApplicationStore(
      JdbcTemplate jdbc, TransactionOperations transactions, ObjectMapper objectMapper) {
    this.jdbc = Objects.requireNonNull(jdbc);
    this.transactions = Objects.requireNonNull(transactions);
    this.objectMapper = Objects.requireNonNull(objectMapper);
  }

  @Override
  public boolean isActiveImportTarget(UUID workspaceId, UUID jobId) {
    Boolean result =
        transactions.execute(
            status -> {
              setTenantContext(workspaceId);
              return jdbc.queryForObject(
                  """
                  SELECT EXISTS (
                      SELECT 1 FROM job
                      WHERE workspace_id = ? AND id = ? AND status = 'ACTIVE'
                  )
                  """,
                  Boolean.class,
                  workspaceId,
                  jobId);
            });
    return Boolean.TRUE.equals(result);
  }

  @Override
  public CandidateApplicationResult saveOrMatch(
      UUID workspaceId,
      UUID candidateId,
      UUID applicationId,
      CandidateApplicationCommand command,
      Instant recordedAt) {
    Objects.requireNonNull(workspaceId, "workspaceId is required");
    Objects.requireNonNull(candidateId, "candidateId is required");
    Objects.requireNonNull(applicationId, "applicationId is required");
    Objects.requireNonNull(command, "command is required");
    Objects.requireNonNull(recordedAt, "recordedAt is required");
    CandidateApplicationResult result =
        transactions.execute(
            status -> saveInTenant(workspaceId, candidateId, applicationId, command, recordedAt));
    return Objects.requireNonNull(result, "transaction result is required");
  }

  private CandidateApplicationResult saveInTenant(
      UUID workspaceId,
      UUID candidateId,
      UUID applicationId,
      CandidateApplicationCommand command,
      Instant recordedAt) {
    setTenantContext(workspaceId);
    CandidateData candidate = command.candidate();
    List<UUID> insertedCandidateIds =
        jdbc.query(
            """
            INSERT INTO candidate(
                id, workspace_id, email, normalized_email, first_name, last_name,
                phone, location, current_title, current_company, skills_text,
                experience_months, created_at, updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT (workspace_id, normalized_email) DO NOTHING
            RETURNING id
            """,
            (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
            candidateId,
            workspaceId,
            candidate.email(),
            candidate.email(),
            candidate.firstName(),
            candidate.lastName(),
            candidate.phone(),
            candidate.location(),
            candidate.currentTitle(),
            candidate.currentCompany(),
            candidate.skills(),
            candidate.experienceMonths(),
            Timestamp.from(recordedAt),
            Timestamp.from(recordedAt));
    boolean candidateCreated = !insertedCandidateIds.isEmpty();
    UUID persistedCandidateId =
        candidateCreated
            ? insertedCandidateIds.getFirst()
            : jdbc.queryForObject(
                "SELECT id FROM candidate WHERE workspace_id = ? AND normalized_email = ?",
                UUID.class,
                workspaceId,
                candidate.email());

    ApplicationData application = command.application();
    List<UUID> insertedApplicationIds =
        jdbc.query(
            """
            INSERT INTO application(
                id, workspace_id, candidate_id, job_id, stage, status, source, applied_at,
                notice_days, available_from, current_ctc_currency, current_ctc_minor,
                expected_ctc_currency, expected_ctc_minor, form_answers, created_at, updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?)
            ON CONFLICT (workspace_id, candidate_id, job_id) DO NOTHING
            RETURNING id
            """,
            (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
            applicationId,
            workspaceId,
            persistedCandidateId,
            command.jobId(),
            "APPLIED",
            "ACTIVE",
            application.source(),
            application.appliedAt(),
            application.noticeDays(),
            application.availableFrom(),
            currency(application.currentCompensation()),
            minorUnits(application.currentCompensation()),
            currency(application.expectedCompensation()),
            minorUnits(application.expectedCompensation()),
            json(application),
            Timestamp.from(recordedAt),
            Timestamp.from(recordedAt));
    boolean applicationCreated = !insertedApplicationIds.isEmpty();
    UUID persistedApplicationId =
        applicationCreated
            ? insertedApplicationIds.getFirst()
            : jdbc.queryForObject(
                """
                SELECT id FROM application
                WHERE workspace_id = ? AND candidate_id = ? AND job_id = ?
                """,
                UUID.class,
                workspaceId,
                persistedCandidateId,
                command.jobId());
    return new CandidateApplicationResult(
        persistedCandidateId, persistedApplicationId, candidateCreated, applicationCreated);
  }

  private String json(ApplicationData application) {
    try {
      return objectMapper.writeValueAsString(application.formAnswers());
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("form answers must be JSON serializable", exception);
    }
  }

  private static String currency(AnnualCompensation compensation) {
    return compensation == null ? null : compensation.currency();
  }

  private static Long minorUnits(AnnualCompensation compensation) {
    return compensation == null ? null : compensation.minorUnits();
  }

  private void setTenantContext(UUID workspaceId) {
    jdbc.queryForObject(
        "SELECT set_config('app.current_workspace_id', ?, true)",
        String.class,
        workspaceId.toString());
    jdbc.execute("SET LOCAL ROLE talon_app");
  }
}
