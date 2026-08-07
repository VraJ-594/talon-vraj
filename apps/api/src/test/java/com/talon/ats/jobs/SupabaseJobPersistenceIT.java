package com.talon.ats.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.talon.ats.jobs.domain.Job;
import com.talon.ats.jobs.domain.JobStatus;
import com.talon.ats.jobs.infrastructure.persistence.JdbcJobRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class SupabaseJobPersistenceIT {

  @Test
  void savesAndListsImportTargetsInsideTheRequestedWorkspaceOnly() {
    TestDatabase database = database();
    JdbcTemplate jdbc = database.jdbc();
    UUID workspaceId = UUID.randomUUID();
    UUID otherWorkspaceId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    Instant now = Instant.now();

    try {
      jdbc.update(
          "INSERT INTO workspace(id,name,slug,default_timezone,status) VALUES (?,?,?,?,?)",
          workspaceId,
          "Job Test",
          "job-test-" + workspaceId,
          "UTC",
          "ACTIVE");

      Job saved =
          database
              .repository()
              .save(
                  new Job(
                      jobId,
                      workspaceId,
                      "Backend Engineer",
                      "Engineering",
                      "Pune",
                      JobStatus.ACTIVE,
                      0,
                      now,
                      now));

      assertThat(saved.id()).isEqualTo(jobId);
      assertThat(database.repository().findImportTargets(workspaceId))
          .singleElement()
          .satisfies(
              job -> {
                assertThat(job.title()).isEqualTo("Backend Engineer");
                assertThat(job.location()).isEqualTo("Pune");
              });
      assertThat(database.repository().findImportTargets(otherWorkspaceId)).isEmpty();
    } finally {
      jdbc.update("DELETE FROM job WHERE id = ?", jobId);
      jdbc.update("DELETE FROM workspace WHERE id = ?", workspaceId);
    }
  }

  private static TestDatabase database() {
    Map<String, String> environment = System.getenv();
    String url = required(environment, "DATABASE_URL");
    String username = required(environment, "DATABASE_USERNAME");
    String password = required(environment, "DATABASE_PASSWORD");
    Flyway.configure()
        .dataSource(url, username, password)
        .locations("classpath:db/migration")
        .load()
        .migrate();
    DriverManagerDataSource dataSource = new DriverManagerDataSource(url, username, password);
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    JdbcJobRepository repository =
        new JdbcJobRepository(
            jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
    return new TestDatabase(jdbc, repository);
  }

  private static String required(Map<String, String> environment, String name) {
    String value = environment.get(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " is required");
    }
    return value;
  }

  private record TestDatabase(JdbcTemplate jdbc, JdbcJobRepository repository) {}
}
