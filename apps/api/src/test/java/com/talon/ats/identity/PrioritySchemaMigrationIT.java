package com.talon.ats.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PrioritySchemaMigrationIT {

  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:17-alpine")
          .withDatabaseName("talon")
          .withUsername("talon")
          .withPassword("talon_test");

  @BeforeAll
  static void migrate() {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:db/migration")
        .load()
        .migrate();
  }

  @Test
  void migrationCreatesPriorityTablesAndTenantConstraints() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID membershipId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID candidateId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();

    try (Connection connection = connection()) {
      setWorkspace(connection, workspaceId);
      execute(
          connection,
          "INSERT INTO workspace(id,name,slug,default_timezone,status) VALUES (?,?,?,?,?)",
          workspaceId,
          "Acme Hiring",
          "acme-hiring",
          "Asia/Kolkata",
          "ACTIVE");
      execute(
          connection,
          "INSERT INTO app_user(id,email,normalized_email,display_name,password_hash,status,default_workspace_id) VALUES (?,?,?,?,?,?,?)",
          userId,
          "Admin@example.com",
          "admin@example.com",
          "Admin",
          "$2a$12$WjJ0PmyFD6h7xjNQxJV13u98Ot4GY9NaAcNn0me04X6NxoJ/Oc9cW",
          "ACTIVE",
          workspaceId);
      execute(
          connection,
          "INSERT INTO workspace_membership(id,workspace_id,user_id,role,status) VALUES (?,?,?,?,?)",
          membershipId,
          workspaceId,
          userId,
          "WORKSPACE_ADMIN",
          "ACTIVE");
      execute(
          connection,
          "INSERT INTO job(id,workspace_id,title,department_name,status) VALUES (?,?,?,?,?)",
          jobId,
          workspaceId,
          "Senior Engineer",
          "Engineering",
          "ACTIVE");
      execute(
          connection,
          "INSERT INTO candidate(id,workspace_id,email,normalized_email,first_name,last_name) VALUES (?,?,?,?,?,?)",
          candidateId,
          workspaceId,
          "Candidate@example.com",
          "candidate@example.com",
          "Test",
          "Candidate");
      execute(
          connection,
          "INSERT INTO application(id,workspace_id,candidate_id,job_id,stage,source,applied_at,expected_ctc_currency,expected_ctc_minor) VALUES (?,?,?,?,?,?,?,?,?)",
          applicationId,
          workspaceId,
          candidateId,
          jobId,
          "APPLIED",
          "GOOGLE_FORM",
          LocalDate.parse("2026-08-07"),
          "INR",
          400_000_000L);

      assertThat(count(connection, "workspace")).isEqualTo(1);
      assertThat(count(connection, "candidate")).isEqualTo(1);
      assertThat(count(connection, "application")).isEqualTo(1);

      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO candidate(id,workspace_id,email,normalized_email,first_name,last_name) VALUES (?,?,?,?,?,?)",
                      UUID.randomUUID(),
                      workspaceId,
                      "CANDIDATE@example.com",
                      "candidate@example.com",
                      "Duplicate",
                      "Candidate"))
          .isInstanceOf(SQLException.class);
      connection.rollback();
    }
  }

  @Test
  void forcedRlsHidesAndRejectsRowsOutsideTheWorkspaceContext() throws Exception {
    UUID workspaceA = UUID.randomUUID();
    UUID workspaceB = UUID.randomUUID();

    try (Connection connection = connection()) {
      setWorkspace(connection, workspaceA);
      execute(
          connection,
          "INSERT INTO workspace(id,name,slug,default_timezone,status) VALUES (?,?,?,?,?)",
          workspaceA,
          "Workspace A",
          "workspace-a-" + workspaceA,
          "UTC",
          "ACTIVE");
      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO workspace(id,name,slug,default_timezone,status) VALUES (?,?,?,?,?)",
                      workspaceB,
                      "Workspace B",
                      "workspace-b-" + workspaceB,
                      "UTC",
                      "ACTIVE"))
          .isInstanceOf(SQLException.class);
      connection.rollback();
    }
  }

  @Test
  void normalizedAccountEmailIsUnique() throws Exception {
    try (Connection connection = connection()) {
      execute(
          connection,
          "INSERT INTO app_user(id,email,normalized_email,display_name,password_hash,status) VALUES (?,?,?,?,?,?)",
          UUID.randomUUID(),
          "Admin@example.com",
          "admin@example.com",
          "Admin",
          "$2a$12$WjJ0PmyFD6h7xjNQxJV13u98Ot4GY9NaAcNn0me04X6NxoJ/Oc9cW",
          "ACTIVE");

      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO app_user(id,email,normalized_email,display_name,password_hash,status) VALUES (?,?,?,?,?,?)",
                      UUID.randomUUID(),
                      "ADMIN@example.com",
                      "admin@example.com",
                      "Another Admin",
                      "$2a$12$WjJ0PmyFD6h7xjNQxJV13u98Ot4GY9NaAcNn0me04X6NxoJ/Oc9cW",
                      "ACTIVE"))
          .isInstanceOf(SQLException.class);
      connection.rollback();
    }
  }

  @Test
  void oneApplicationPerCandidateJobIsEnforced() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID candidateId = UUID.randomUUID();

    try (Connection connection = connection()) {
      seedJobAndCandidate(connection, workspaceId, jobId, candidateId);
      insertApplication(connection, UUID.randomUUID(), workspaceId, candidateId, jobId, "INR", 1L);

      assertThatThrownBy(
              () ->
                  insertApplication(
                      connection, UUID.randomUUID(), workspaceId, candidateId, jobId, "INR", 2L))
          .isInstanceOf(SQLException.class);
      connection.rollback();
    }
  }

  @Test
  void compensationRequiresCurrencyAndNonNegativeMinorUnitsTogether() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID candidateId = UUID.randomUUID();

    try (Connection connection = connection()) {
      seedJobAndCandidate(connection, workspaceId, jobId, candidateId);

      assertThatThrownBy(
              () ->
                  insertApplication(
                      connection, UUID.randomUUID(), workspaceId, candidateId, jobId, "INR", -1L))
          .isInstanceOf(SQLException.class);
      connection.rollback();
    }
  }

  private static void seedJobAndCandidate(
      Connection connection, UUID workspaceId, UUID jobId, UUID candidateId) throws SQLException {
    setWorkspace(connection, workspaceId);
    execute(
        connection,
        "INSERT INTO workspace(id,name,slug,default_timezone,status) VALUES (?,?,?,?,?)",
        workspaceId,
        "Workspace",
        "workspace-" + workspaceId,
        "UTC",
        "ACTIVE");
    execute(
        connection,
        "INSERT INTO job(id,workspace_id,title,status) VALUES (?,?,?,?)",
        jobId,
        workspaceId,
        "Engineer",
        "ACTIVE");
    execute(
        connection,
        "INSERT INTO candidate(id,workspace_id,email,normalized_email,first_name,last_name) VALUES (?,?,?,?,?,?)",
        candidateId,
        workspaceId,
        candidateId + "@example.com",
        candidateId + "@example.com",
        "Test",
        "Candidate");
  }

  private static void insertApplication(
      Connection connection,
      UUID applicationId,
      UUID workspaceId,
      UUID candidateId,
      UUID jobId,
      String currency,
      Long expectedCtcMinor)
      throws SQLException {
    execute(
        connection,
        "INSERT INTO application(id,workspace_id,candidate_id,job_id,stage,applied_at,expected_ctc_currency,expected_ctc_minor) VALUES (?,?,?,?,?,?,?,?)",
        applicationId,
        workspaceId,
        candidateId,
        jobId,
        "APPLIED",
        LocalDate.parse("2026-08-07"),
        currency,
        expectedCtcMinor);
  }

  private static Connection connection() throws SQLException {
    Connection connection =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    connection.setAutoCommit(false);
    try (Statement statement = connection.createStatement()) {
      statement.execute("SET ROLE talon_app");
    }
    return connection;
  }

  private static void setWorkspace(Connection connection, UUID workspaceId) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement("SELECT set_config('app.current_workspace_id', ?, false)")) {
      statement.setString(1, workspaceId.toString());
      statement.execute();
    }
  }

  private static void execute(Connection connection, String sql, Object... values)
      throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int index = 0; index < values.length; index++) {
        statement.setObject(index + 1, values[index]);
      }
      statement.executeUpdate();
    }
  }

  private static int count(Connection connection, String table) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("SELECT count(*) FROM " + table)) {
      result.next();
      return result.getInt(1);
    }
  }
}
