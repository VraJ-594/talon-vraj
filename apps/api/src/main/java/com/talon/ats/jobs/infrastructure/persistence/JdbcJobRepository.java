package com.talon.ats.jobs.infrastructure.persistence;

import com.talon.ats.jobs.application.JobRepository;
import com.talon.ats.jobs.domain.Job;
import com.talon.ats.jobs.domain.JobStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionOperations;

public final class JdbcJobRepository implements JobRepository {

  private final JdbcTemplate jdbc;
  private final TransactionOperations transactions;

  public JdbcJobRepository(JdbcTemplate jdbc, TransactionOperations transactions) {
    this.jdbc = Objects.requireNonNull(jdbc);
    this.transactions = Objects.requireNonNull(transactions);
  }

  @Override
  public List<Job> findImportTargets(UUID workspaceId) {
    Objects.requireNonNull(workspaceId, "workspaceId is required");
    List<Job> result =
        transactions.execute(
            status -> {
              setTenantContext(workspaceId);
              return jdbc.query(
                  """
                  SELECT id, workspace_id, title, department_name, location, status,
                         version, created_at, updated_at
                  FROM job
                  WHERE workspace_id = ? AND status IN ('ACTIVE', 'ON_HOLD')
                  ORDER BY title, id
                  """,
                  JdbcJobRepository::mapJob,
                  workspaceId);
            });
    return result == null ? List.of() : List.copyOf(result);
  }

  @Override
  public Job save(Job job) {
    Objects.requireNonNull(job, "job is required");
    transactions.executeWithoutResult(
        status -> {
          setTenantContext(job.workspaceId());
          jdbc.update(
              """
              INSERT INTO job(
                  id, workspace_id, title, department_name, location, status,
                  version, created_at, updated_at)
              VALUES (?,?,?,?,?,?,?,?,?)
              """,
              job.id(),
              job.workspaceId(),
              job.title(),
              job.department(),
              job.location(),
              job.status().name(),
              job.version(),
              Timestamp.from(job.createdAt()),
              Timestamp.from(job.updatedAt()));
        });
    return job;
  }

  private void setTenantContext(UUID workspaceId) {
    jdbc.queryForObject(
        "SELECT set_config('app.current_workspace_id', ?, true)",
        String.class,
        workspaceId.toString());
    jdbc.execute("SET LOCAL ROLE talon_app");
  }

  private static Job mapJob(ResultSet resultSet, int rowNumber) throws SQLException {
    return new Job(
        resultSet.getObject("id", UUID.class),
        resultSet.getObject("workspace_id", UUID.class),
        resultSet.getString("title"),
        resultSet.getString("department_name"),
        resultSet.getString("location"),
        JobStatus.valueOf(resultSet.getString("status")),
        resultSet.getLong("version"),
        resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
        resultSet.getObject("updated_at", OffsetDateTime.class).toInstant());
  }
}
