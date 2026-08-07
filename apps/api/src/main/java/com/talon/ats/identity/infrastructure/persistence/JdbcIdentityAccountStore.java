package com.talon.ats.identity.infrastructure.persistence;

import com.talon.ats.identity.application.AuthenticationAccount;
import com.talon.ats.identity.application.IdentityAccountStore;
import com.talon.ats.identity.application.IdentityWorkspaceBootstrapStore;
import com.talon.ats.identity.contract.WorkspaceRole;
import com.talon.ats.identity.domain.AppUser;
import com.talon.ats.identity.domain.AppUserStatus;
import com.talon.ats.identity.domain.RefreshSession;
import com.talon.ats.identity.domain.WorkspaceBootstrap;
import com.talon.ats.identity.domain.WorkspaceMembership;
import com.talon.ats.identity.domain.WorkspaceMembershipStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionOperations;

public final class JdbcIdentityAccountStore
    implements IdentityAccountStore, IdentityWorkspaceBootstrapStore {

  private static final String ACCOUNT_SQL =
      """
      SELECT app.id, app.email, app.normalized_email, app.display_name, app.password_hash,
             app.status, app.default_workspace_id, app.created_at, app.last_login_at,
             workspace.name AS workspace_name
      FROM app_user app
      LEFT JOIN workspace ON workspace.id = app.default_workspace_id
      WHERE app.normalized_email = ?
      """;

  private static final String MEMBERSHIP_SQL =
      """
      SELECT id, workspace_id, user_id, role, status, joined_at, version
      FROM workspace_membership
      WHERE workspace_id = ? AND user_id = ?
      """;

  private final JdbcTemplate jdbc;
  private final TransactionOperations transactions;

  public JdbcIdentityAccountStore(JdbcTemplate jdbc, TransactionOperations transactions) {
    this.jdbc = Objects.requireNonNull(jdbc);
    this.transactions = Objects.requireNonNull(transactions);
  }

  @Override
  public Optional<AuthenticationAccount> findByNormalizedEmail(String normalizedEmail) {
    Objects.requireNonNull(normalizedEmail, "normalizedEmail is required");
    Optional<AuthenticationAccount> result =
        transactions.execute(
            status -> {
              List<AccountWorkspace> accounts =
                  jdbc.query(ACCOUNT_SQL, JdbcIdentityAccountStore::mapAccount, normalizedEmail);
              if (accounts.isEmpty() || accounts.getFirst().workspaceId() == null) {
                return Optional.empty();
              }

              AccountWorkspace account = accounts.getFirst();
              setTenantContext(account.workspaceId());
              List<WorkspaceMembership> memberships =
                  jdbc.query(
                      MEMBERSHIP_SQL,
                      JdbcIdentityAccountStore::mapMembership,
                      account.workspaceId(),
                      account.user().id());
              return memberships.stream()
                  .findFirst()
                  .map(
                      membership ->
                          new AuthenticationAccount(
                              account.user(), membership, account.workspaceName()));
            });
    return result == null ? Optional.empty() : result;
  }

  @Override
  public boolean hasMembershipByNormalizedEmail(String normalizedEmail) {
    Objects.requireNonNull(normalizedEmail, "normalizedEmail is required");
    Boolean exists =
        jdbc.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1
                FROM app_user app
                JOIN workspace_membership membership ON membership.user_id = app.id
                WHERE app.normalized_email = ?
            )
            """,
            Boolean.class,
            normalizedEmail);
    return Boolean.TRUE.equals(exists);
  }

  @Override
  public void save(WorkspaceBootstrap bootstrap) {
    Objects.requireNonNull(bootstrap, "workspaceBootstrap is required");
    transactions.executeWithoutResult(
        status -> {
          jdbc.update(
              """
              INSERT INTO workspace(
                  id, name, slug, default_timezone, status, retention_months,
                  version, created_at, updated_at)
              VALUES (?,?,?,?,?,?,?,?,?)
              """,
              bootstrap.workspace().id(),
              bootstrap.workspace().name(),
              bootstrap.workspace().slug(),
              bootstrap.workspace().defaultTimezone(),
              "ACTIVE",
              bootstrap.workspace().retentionMonths(),
              bootstrap.workspace().version(),
              timestamp(bootstrap.workspace().createdAt()),
              timestamp(bootstrap.workspace().createdAt()));
          jdbc.update(
              """
              INSERT INTO app_user(
                  id, email, normalized_email, display_name, password_hash, status,
                  default_workspace_id, last_login_at, created_at, updated_at)
              VALUES (?,?,?,?,?,?,?,?,?,?)
              """,
              bootstrap.user().id(),
              bootstrap.user().email(),
              bootstrap.user().normalizedEmail(),
              bootstrap.user().displayName(),
              bootstrap.user().passwordHash(),
              bootstrap.user().status().name(),
              bootstrap.workspace().id(),
              timestamp(bootstrap.user().lastLoginAt()),
              timestamp(bootstrap.user().createdAt()),
              timestamp(bootstrap.user().createdAt()));
          jdbc.update(
              """
              INSERT INTO workspace_membership(
                  id, workspace_id, user_id, role, status, version, joined_at, updated_at)
              VALUES (?,?,?,?,?,?,?,?)
              """,
              bootstrap.membership().id(),
              bootstrap.membership().workspaceId(),
              bootstrap.membership().userId(),
              bootstrap.membership().role().name(),
              bootstrap.membership().status().name(),
              bootstrap.membership().version(),
              timestamp(bootstrap.membership().joinedAt()),
              timestamp(bootstrap.membership().joinedAt()));
        });
  }

  @Override
  public void completeSuccessfulLogin(RefreshSession refreshSession, Instant loggedInAt) {
    Objects.requireNonNull(refreshSession, "refreshSession is required");
    Objects.requireNonNull(loggedInAt, "loggedInAt is required");
    transactions.executeWithoutResult(
        status -> {
          setTenantContext(refreshSession.workspaceId());
          jdbc.update(
              """
              INSERT INTO refresh_session(
                  id, workspace_id, user_id, token_hash, family_id, parent_id,
                  expires_at, used_at, revoked_at, created_at)
              VALUES (?,?,?,?,?,?,?,?,?,?)
              """,
              refreshSession.id(),
              refreshSession.workspaceId(),
              refreshSession.userId(),
              refreshSession.tokenHash(),
              refreshSession.familyId(),
              refreshSession.parentId(),
              timestamp(refreshSession.expiresAt()),
              timestamp(refreshSession.usedAt()),
              timestamp(refreshSession.revokedAt()),
              timestamp(refreshSession.createdAt()));
          int updated =
              jdbc.update(
                  "UPDATE app_user SET last_login_at = ?, updated_at = ? WHERE id = ?",
                  timestamp(loggedInAt),
                  timestamp(loggedInAt),
                  refreshSession.userId());
          if (updated != 1) {
            throw new IllegalStateException("authenticated account no longer exists");
          }
        });
  }

  private void setTenantContext(UUID workspaceId) {
    jdbc.queryForObject(
        "SELECT set_config('app.current_workspace_id', ?, true)",
        String.class,
        workspaceId.toString());
    jdbc.execute("SET LOCAL ROLE talon_app");
  }

  private static AccountWorkspace mapAccount(ResultSet resultSet, int rowNumber)
      throws SQLException {
    UUID workspaceId = resultSet.getObject("default_workspace_id", UUID.class);
    AppUser user =
        new AppUser(
            resultSet.getObject("id", UUID.class),
            resultSet.getString("email"),
            resultSet.getString("normalized_email"),
            resultSet.getString("display_name"),
            resultSet.getString("password_hash"),
            AppUserStatus.valueOf(resultSet.getString("status")),
            instant(resultSet, "created_at"),
            instant(resultSet, "last_login_at"));
    return new AccountWorkspace(user, workspaceId, resultSet.getString("workspace_name"));
  }

  private static WorkspaceMembership mapMembership(ResultSet resultSet, int rowNumber)
      throws SQLException {
    return new WorkspaceMembership(
        resultSet.getObject("id", UUID.class),
        resultSet.getObject("workspace_id", UUID.class),
        resultSet.getObject("user_id", UUID.class),
        WorkspaceRole.valueOf(resultSet.getString("role")),
        WorkspaceMembershipStatus.valueOf(resultSet.getString("status")),
        instant(resultSet, "joined_at"),
        resultSet.getLong("version"));
  }

  private static Instant instant(ResultSet resultSet, String column) throws SQLException {
    OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }

  private static Timestamp timestamp(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }

  private record AccountWorkspace(AppUser user, UUID workspaceId, String workspaceName) {}
}
