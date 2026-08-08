package com.talon.ats.candidates;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talon.ats.candidates.application.ApplicationData;
import com.talon.ats.candidates.application.CandidateApplicationCommand;
import com.talon.ats.candidates.application.CandidateApplicationDetail;
import com.talon.ats.candidates.application.CandidateApplicationResult;
import com.talon.ats.candidates.application.CandidateApplicationSlice;
import com.talon.ats.candidates.application.CandidateData;
import com.talon.ats.candidates.application.CandidateResumeStatus;
import com.talon.ats.candidates.infrastructure.persistence.JdbcCandidateApplicationQueryStore;
import com.talon.ats.candidates.infrastructure.persistence.JdbcCandidateApplicationStore;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class SupabaseCandidateApplicationPersistenceIT {

  @Test
  void matchesCandidateEmailAndApplicationReplayWithinOneWorkspace() {
    TestDatabase database = database();
    JdbcTemplate jdbc = database.jdbc();
    UUID workspaceId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    CandidateApplicationCommand command = command(jobId);

    try {
      seedWorkspaceAndJob(jdbc, workspaceId, jobId);
      assertThat(database.store().isActiveImportTarget(workspaceId, jobId)).isTrue();

      CandidateApplicationResult first =
          database
              .store()
              .saveOrMatch(
                  workspaceId,
                  UUID.randomUUID(),
                  UUID.randomUUID(),
                  command,
                  Instant.parse("2026-08-07T18:30:00Z"));
      CandidateApplicationResult replay =
          database
              .store()
              .saveOrMatch(
                  workspaceId,
                  UUID.randomUUID(),
                  UUID.randomUUID(),
                  command,
                  Instant.parse("2026-08-07T18:31:00Z"));

      assertThat(first.candidateCreated()).isTrue();
      assertThat(first.applicationCreated()).isTrue();
      assertThat(replay.candidateId()).isEqualTo(first.candidateId());
      assertThat(replay.applicationId()).isEqualTo(first.applicationId());
      assertThat(replay.candidateCreated()).isFalse();
      assertThat(replay.applicationCreated()).isFalse();
      assertThat(
              jdbc.queryForObject(
                  "SELECT expected_ctc_minor FROM application WHERE id = ?",
                  Long.class,
                  first.applicationId()))
          .isEqualTo(400_000_000L);

      CandidateApplicationSlice page = database.queryStore().list(workspaceId, null, 25);
      assertThat(page.items())
          .singleElement()
          .satisfies(
              application -> {
                assertThat(application.applicationId()).isEqualTo(first.applicationId());
                assertThat(application.candidateName()).isEqualTo("Nila Raman");
                assertThat(application.resumeStatus()).isEqualTo(CandidateResumeStatus.NO_RESUME);
              });
      assertThat(database.queryStore().list(UUID.randomUUID(), null, 25).items()).isEmpty();

      CandidateApplicationDetail detail =
          database.queryStore().findDetail(workspaceId, first.applicationId()).orElseThrow();
      assertThat(detail.email()).isEqualTo("nila@example.com");
      assertThat(detail.additionalAnswers()).containsEntry("preferredTeam", "Platform");

      UUID fileId = UUID.randomUUID();
      UUID versionId = UUID.randomUUID();
      jdbc.update(
          """
          INSERT INTO candidate_file(
              id, workspace_id, application_id, file_name, object_key,
              status, content_type, size_bytes)
          VALUES (?,?,?,?,?,'CLEAN','application/pdf',128)
          """,
          fileId,
          workspaceId,
          first.applicationId(),
          "nila-resume.pdf",
          "clean/" + workspaceId + "/resumes/" + fileId + "/" + versionId + ".pdf");
      assertThat(database.queryStore().findCleanResume(workspaceId, first.applicationId()))
          .hasValueSatisfying(
              resume -> {
                assertThat(resume.fileName()).isEqualTo("nila-resume.pdf");
                assertThat(resume.objectKey().isCleanResume()).isTrue();
              });
    } finally {
      jdbc.update("DELETE FROM candidate_file WHERE workspace_id = ?", workspaceId);
      jdbc.update("DELETE FROM application WHERE workspace_id = ?", workspaceId);
      jdbc.update("DELETE FROM candidate WHERE workspace_id = ?", workspaceId);
      jdbc.update("DELETE FROM job WHERE workspace_id = ?", workspaceId);
      jdbc.update("DELETE FROM workspace WHERE id = ?", workspaceId);
    }
  }

  private static CandidateApplicationCommand command(UUID jobId) {
    return new CandidateApplicationCommand(
        jobId,
        new CandidateData(
            "nila@example.com",
            "Nila",
            "Raman",
            null,
            "Pune",
            "Engineer",
            "Example Ltd",
            "Java, PostgreSQL",
            72),
        new ApplicationData(
            "GOOGLE_FORM",
            LocalDate.parse("2026-08-07"),
            30,
            LocalDate.parse("2026-09-06"),
            null,
            new com.talon.ats.candidates.application.AnnualCompensation("INR", 400_000_000L),
            Map.of("preferredTeam", "Platform")));
  }

  private static void seedWorkspaceAndJob(JdbcTemplate jdbc, UUID workspaceId, UUID jobId) {
    jdbc.update(
        "INSERT INTO workspace(id,name,slug,default_timezone,status) VALUES (?,?,?,?,?)",
        workspaceId,
        "Candidate Test",
        "candidate-test-" + workspaceId,
        "UTC",
        "ACTIVE");
    jdbc.update(
        "INSERT INTO job(id,workspace_id,title,department_name,location,status) VALUES (?,?,?,?,?,?)",
        jobId,
        workspaceId,
        "Backend Engineer",
        "Engineering",
        "Pune",
        "ACTIVE");
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
    TransactionTemplate transactions =
        new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    ObjectMapper objectMapper = new ObjectMapper();
    JdbcCandidateApplicationStore store =
        new JdbcCandidateApplicationStore(jdbc, transactions, objectMapper);
    JdbcCandidateApplicationQueryStore queryStore =
        new JdbcCandidateApplicationQueryStore(jdbc, transactions, objectMapper);
    return new TestDatabase(jdbc, store, queryStore);
  }

  private static String required(Map<String, String> environment, String name) {
    String value = environment.get(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " is required");
    }
    return value;
  }

  private record TestDatabase(
      JdbcTemplate jdbc,
      JdbcCandidateApplicationStore store,
      JdbcCandidateApplicationQueryStore queryStore) {}
}
