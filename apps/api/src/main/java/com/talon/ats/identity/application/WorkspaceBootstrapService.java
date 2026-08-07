package com.talon.ats.identity.application;

import com.talon.ats.identity.contract.WorkspaceRole;
import com.talon.ats.identity.domain.AppUser;
import com.talon.ats.identity.domain.AppUserStatus;
import com.talon.ats.identity.domain.Workspace;
import com.talon.ats.identity.domain.WorkspaceBootstrap;
import com.talon.ats.identity.domain.WorkspaceMembership;
import com.talon.ats.identity.domain.WorkspaceMembershipStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class WorkspaceBootstrapService {

  private static final int DEFAULT_RETENTION_MONTHS = 24;
  private static final String BCRYPT_HASH_PATTERN =
      "^\\$2[aby]\\$(0[4-9]|[12][0-9]|3[01])\\$[./A-Za-z0-9]{53}$";

  private final IdentityWorkspaceBootstrapStore store;
  private final Supplier<UUID> idGenerator;
  private final Clock clock;

  public WorkspaceBootstrapService(
      IdentityWorkspaceBootstrapStore store, Supplier<UUID> idGenerator, Clock clock) {
    this.store = Objects.requireNonNull(store);
    this.idGenerator = Objects.requireNonNull(idGenerator);
    this.clock = Objects.requireNonNull(clock);
  }

  public BootstrapWorkspaceResult bootstrap(BootstrapWorkspaceCommand command) {
    Objects.requireNonNull(command, "command is required");
    String email = required(command.email(), "email");
    String normalizedEmail = normalizedEmail(email);

    if (store.hasMembershipByNormalizedEmail(normalizedEmail)) {
      throw new WorkspaceBootstrapNotAllowedException("Account already belongs to a workspace");
    }

    Instant now = clock.instant();
    AppUser user =
        new AppUser(
            nextId(),
            email,
            normalizedEmail,
            required(command.displayName(), "displayName"),
            bcryptHash(command.passwordHash()),
            AppUserStatus.ACTIVE,
            now,
            now);
    Workspace workspace =
        new Workspace(
            nextId(),
            required(command.workspaceName(), "workspaceName"),
            normalizedSlug(command.workspaceSlug()),
            validTimezone(command.defaultTimezone()),
            DEFAULT_RETENTION_MONTHS,
            now,
            user.id(),
            0);
    WorkspaceMembership membership =
        new WorkspaceMembership(
            nextId(),
            workspace.id(),
            user.id(),
            WorkspaceRole.WORKSPACE_ADMIN,
            WorkspaceMembershipStatus.ACTIVE,
            now,
            0);

    store.save(new WorkspaceBootstrap(user, workspace, membership));
    return new BootstrapWorkspaceResult(workspace.id(), membership.id());
  }

  private UUID nextId() {
    return Objects.requireNonNull(idGenerator.get(), "generated ID is required");
  }

  private static String required(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return value.trim();
  }

  private static String normalizedEmail(String value) {
    return required(value, "email").toLowerCase(Locale.ROOT);
  }

  private static String bcryptHash(String value) {
    String passwordHash = required(value, "passwordHash");
    if (!passwordHash.matches(BCRYPT_HASH_PATTERN)) {
      throw new IllegalArgumentException("passwordHash must be a BCrypt hash");
    }
    return passwordHash;
  }

  private static String normalizedSlug(String value) {
    String slug =
        required(value, "workspaceSlug")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-|-$)", "");
    if (slug.isBlank()) {
      throw new IllegalArgumentException("workspaceSlug must contain letters or numbers");
    }
    return slug;
  }

  private static String validTimezone(String value) {
    String timezone = required(value, "defaultTimezone");
    ZoneId.of(timezone);
    return timezone;
  }
}
