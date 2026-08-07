package com.talon.ats.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.talon.ats.identity.application.AuthenticationAccount;
import com.talon.ats.identity.application.BootstrapWorkspaceCommand;
import com.talon.ats.identity.application.IdentityWorkspaceBootstrapStore;
import com.talon.ats.identity.application.WorkspaceBootstrapNotAllowedException;
import com.talon.ats.identity.application.WorkspaceBootstrapService;
import com.talon.ats.identity.contract.WorkspaceRole;
import com.talon.ats.identity.domain.RefreshSession;
import com.talon.ats.identity.infrastructure.persistence.JdbcIdentityAccountStore;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class SupabaseIdentityPersistenceIT {

  @Test
  void bootstrapsDemoWorkspaceIdempotentlyThroughThePersistencePort() {
    TestDatabase database = database();
    JdbcTemplate jdbc = database.jdbc();
    JdbcIdentityAccountStore store = database.store();
    IdentityWorkspaceBootstrapStore bootstrapStore = store;
    String normalizedEmail = "bootstrap-" + UUID.randomUUID() + "@example.invalid";
    WorkspaceBootstrapService service =
        new WorkspaceBootstrapService(bootstrapStore, UUID::randomUUID, Clock.systemUTC());

    UUID userId = null;
    UUID workspaceId = null;
    try {
      workspaceId =
          service
              .bootstrap(
                  new BootstrapWorkspaceCommand(
                      normalizedEmail,
                      "Demo Administrator",
                      "$2a$12$WjJ0PmyFD6h7xjNQxJV13u98Ot4GY9NaAcNn0me04X6NxoJ/Oc9cW",
                      "Talon Demo",
                      "demo-" + UUID.randomUUID(),
                      "Asia/Kolkata"))
              .workspaceId();
      userId =
          jdbc.queryForObject(
              "SELECT id FROM app_user WHERE normalized_email = ?", UUID.class, normalizedEmail);

      assertThat(store.findByNormalizedEmail(normalizedEmail)).isPresent();
      org.assertj.core.api.Assertions.assertThatThrownBy(
              () ->
                  service.bootstrap(
                      new BootstrapWorkspaceCommand(
                          normalizedEmail,
                          "Demo Administrator",
                          "$2a$12$WjJ0PmyFD6h7xjNQxJV13u98Ot4GY9NaAcNn0me04X6NxoJ/Oc9cW",
                          "Another Demo",
                          "another-" + UUID.randomUUID(),
                          "UTC")))
          .isInstanceOf(WorkspaceBootstrapNotAllowedException.class);
    } finally {
      cleanup(jdbc, userId, workspaceId);
    }
  }

  @Test
  void loadsDefaultWorkspaceAccountAndAtomicallyRecordsSuccessfulLogin() {
    TestDatabase database = database();
    JdbcTemplate jdbc = database.jdbc();
    JdbcIdentityAccountStore store = database.store();

    UUID workspaceId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID membershipId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    UUID familyId = UUID.randomUUID();
    String normalizedEmail = "auth-" + userId + "@example.invalid";
    Instant loginAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

    try {
      seedAccount(jdbc, workspaceId, userId, membershipId, normalizedEmail);

      Optional<AuthenticationAccount> result = store.findByNormalizedEmail(normalizedEmail);

      assertThat(result).isPresent();
      AuthenticationAccount account = result.orElseThrow();
      assertThat(account.user().id()).isEqualTo(userId);
      assertThat(account.user().normalizedEmail()).isEqualTo(normalizedEmail);
      assertThat(account.membership().workspaceId()).isEqualTo(workspaceId);
      assertThat(account.membership().role()).isEqualTo(WorkspaceRole.WORKSPACE_ADMIN);

      RefreshSession refreshSession =
          new RefreshSession(
              sessionId,
              userId,
              workspaceId,
              "hash-" + sessionId,
              familyId,
              null,
              loginAt.plus(7, ChronoUnit.DAYS),
              null,
              null,
              loginAt);
      store.completeSuccessfulLogin(refreshSession, loginAt);

      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM refresh_session WHERE id = ? AND token_hash = ?",
                  Integer.class,
                  sessionId,
                  refreshSession.tokenHash()))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT last_login_at FROM app_user WHERE id = ?", Instant.class, userId))
          .isEqualTo(loginAt);
    } finally {
      cleanup(jdbc, userId, workspaceId);
    }
  }

  private static TestDatabase database() {
    Map<String, String> environment = System.getenv();
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            required(environment, "DATABASE_URL"),
            required(environment, "DATABASE_USERNAME"),
            required(environment, "DATABASE_PASSWORD"));
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    TransactionTemplate transactions =
        new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    return new TestDatabase(jdbc, new JdbcIdentityAccountStore(jdbc, transactions));
  }

  private static void cleanup(JdbcTemplate jdbc, UUID userId, UUID workspaceId) {
    if (userId != null) {
      jdbc.update("DELETE FROM refresh_session WHERE user_id = ?", userId);
      jdbc.update("DELETE FROM workspace_membership WHERE user_id = ?", userId);
      jdbc.update("DELETE FROM app_user WHERE id = ?", userId);
    }
    if (workspaceId != null) {
      jdbc.update("DELETE FROM workspace WHERE id = ?", workspaceId);
    }
  }

  private static void seedAccount(
      JdbcTemplate jdbc, UUID workspaceId, UUID userId, UUID membershipId, String normalizedEmail) {
    jdbc.update(
        "INSERT INTO workspace(id,name,slug,default_timezone,status) VALUES (?,?,?,?,?)",
        workspaceId,
        "Persistence Test",
        "auth-" + workspaceId,
        "UTC",
        "ACTIVE");
    jdbc.update(
        "INSERT INTO app_user(id,email,normalized_email,display_name,password_hash,status,default_workspace_id) VALUES (?,?,?,?,?,?,?)",
        userId,
        normalizedEmail,
        normalizedEmail,
        "Persistence Test",
        "$2a$12$WjJ0PmyFD6h7xjNQxJV13u98Ot4GY9NaAcNn0me04X6NxoJ/Oc9cW",
        "ACTIVE",
        workspaceId);
    jdbc.update(
        "INSERT INTO workspace_membership(id,workspace_id,user_id,role,status) VALUES (?,?,?,?,?)",
        membershipId,
        workspaceId,
        userId,
        "WORKSPACE_ADMIN",
        "ACTIVE");
  }

  private static String required(Map<String, String> environment, String name) {
    String value = environment.get(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " is required");
    }
    return value;
  }

  private record TestDatabase(JdbcTemplate jdbc, JdbcIdentityAccountStore store) {}
}
