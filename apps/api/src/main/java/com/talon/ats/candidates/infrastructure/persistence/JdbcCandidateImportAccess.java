package com.talon.ats.candidates.infrastructure.persistence;

import com.talon.ats.candidates.application.AnnualCompensation;
import com.talon.ats.candidates.application.ApplicationData;
import com.talon.ats.candidates.application.CandidateApplicationCommand;
import com.talon.ats.candidates.application.CandidateApplicationService;
import com.talon.ats.candidates.application.CandidateData;
import com.talon.ats.candidates.contract.CandidateImportAccess;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionOperations;

public final class JdbcCandidateImportAccess implements CandidateImportAccess {

  private final CandidateApplicationService service;
  private final JdbcTemplate jdbc;
  private final TransactionOperations transactions;

  public JdbcCandidateImportAccess(
      CandidateApplicationService service, JdbcTemplate jdbc, TransactionOperations transactions) {
    this.service = Objects.requireNonNull(service);
    this.jdbc = Objects.requireNonNull(jdbc);
    this.transactions = Objects.requireNonNull(transactions);
  }

  @Override
  public Result createOrMatch(Actor actor, Application application) {
    var result =
        service.createOrMatch(
            new CandidateApplicationService.Actor(
                actor.userId(), actor.workspaceId(), actor.role()),
            new CandidateApplicationCommand(
                application.jobId(),
                new CandidateData(
                    application.email(),
                    application.firstName(),
                    application.lastName(),
                    application.phone(),
                    application.location(),
                    application.currentTitle(),
                    application.currentCompany(),
                    application.skills(),
                    application.experienceMonths()),
                new ApplicationData(
                    application.source(),
                    application.appliedAt(),
                    application.noticeDays(),
                    application.availableFrom(),
                    money(application.currentCompensation()),
                    money(application.expectedCompensation()),
                    application.formAnswers())));
    return new Result(
        result.candidateId(),
        result.applicationId(),
        result.candidateCreated(),
        result.applicationCreated());
  }

  @Override
  public void attachResume(Actor actor, UUID applicationId, Resume resume) {
    Objects.requireNonNull(actor);
    Objects.requireNonNull(applicationId);
    Objects.requireNonNull(resume);
    transactions.executeWithoutResult(
        status -> {
          jdbc.queryForObject(
              "SELECT set_config('app.current_workspace_id', ?, true)",
              String.class,
              actor.workspaceId().toString());
          jdbc.execute("SET LOCAL ROLE talon_app");
          Instant now = Instant.now();
          jdbc.update(
              """
              INSERT INTO candidate_file(
                  id, workspace_id, application_id, file_name, object_key, status,
                  content_type, size_bytes, created_at, updated_at)
              VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
              ON CONFLICT (workspace_id, application_id) DO UPDATE
              SET file_name = EXCLUDED.file_name, object_key = EXCLUDED.object_key,
                  status = EXCLUDED.status, content_type = EXCLUDED.content_type,
                  size_bytes = EXCLUDED.size_bytes, updated_at = EXCLUDED.updated_at
              """,
              resume.fileId(),
              actor.workspaceId(),
              applicationId,
              resume.fileName(),
              resume.objectKey(),
              resume.status(),
              resume.contentType(),
              resume.sizeBytes(),
              Timestamp.from(now),
              Timestamp.from(now));
        });
  }

  private static AnnualCompensation money(Money money) {
    return money == null ? null : new AnnualCompensation(money.currency(), money.minorUnits());
  }
}
