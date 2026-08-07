package com.talon.ats.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.talon.ats.identity.application.BootstrapWorkspaceCommand;
import com.talon.ats.identity.application.BootstrapWorkspaceResult;
import com.talon.ats.identity.application.IdentityWorkspaceBootstrapStore;
import com.talon.ats.identity.application.WorkspaceBootstrapNotAllowedException;
import com.talon.ats.identity.application.WorkspaceBootstrapService;
import com.talon.ats.identity.domain.AppUser;
import com.talon.ats.identity.domain.Workspace;
import com.talon.ats.identity.domain.WorkspaceBootstrap;
import com.talon.ats.identity.domain.WorkspaceMembership;
import com.talon.ats.identity.domain.WorkspaceRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class WorkspaceBootstrapServiceTests {

  private static final Instant NOW = Instant.parse("2026-08-07T10:00:00Z");
  private static final String PASSWORD_HASH =
      "$2a$12$WjJ0PmyFD6h7xjNQxJV13u98Ot4GY9NaAcNn0me04X6NxoJ/Oc9cW";

  @Test
  void createsUserWorkspaceAndAdministratorMembershipAtomically() {
    RecordingStore store = new RecordingStore(false);
    WorkspaceBootstrapService service = service(store);

    BootstrapWorkspaceResult result =
        service.bootstrap(
            new BootstrapWorkspaceCommand(
                " VRAJ@Example.com ",
                " Vraj Dobariya ",
                PASSWORD_HASH,
                " Acme Hiring ",
                " Acme Hiring ",
                "Asia/Kolkata"));

    assertThat(result.workspaceId()).isEqualTo(uuid(2));
    assertThat(result.membershipId()).isEqualTo(uuid(3));
    assertThat(store.saved).isNotNull();

    AppUser user = store.saved.user();
    Workspace workspace = store.saved.workspace();
    WorkspaceMembership membership = store.saved.membership();

    assertThat(user.id()).isEqualTo(uuid(1));
    assertThat(user.email()).isEqualTo("VRAJ@Example.com");
    assertThat(user.normalizedEmail()).isEqualTo("vraj@example.com");
    assertThat(user.passwordHash()).isEqualTo(PASSWORD_HASH);
    assertThat(workspace.name()).isEqualTo("Acme Hiring");
    assertThat(workspace.slug()).isEqualTo("acme-hiring");
    assertThat(workspace.defaultTimezone()).isEqualTo("Asia/Kolkata");
    assertThat(workspace.retentionMonths()).isEqualTo(24);
    assertThat(workspace.createdAt()).isEqualTo(NOW);
    assertThat(membership.userId()).isEqualTo(user.id());
    assertThat(membership.workspaceId()).isEqualTo(workspace.id());
    assertThat(membership.role()).isEqualTo(WorkspaceRole.WORKSPACE_ADMIN);
    assertThat(membership.joinedAt()).isEqualTo(NOW);
  }

  @Test
  void rejectsFirstWorkspaceBootstrapWhenIdentityAlreadyHasMembership() {
    RecordingStore store = new RecordingStore(true);
    WorkspaceBootstrapService service = service(store);

    assertThatThrownBy(
            () ->
                service.bootstrap(
                    new BootstrapWorkspaceCommand(
                        "vraj@example.com",
                        "Vraj",
                        PASSWORD_HASH,
                        "Another Workspace",
                        "another-workspace",
                        "UTC")))
        .isInstanceOf(WorkspaceBootstrapNotAllowedException.class)
        .hasMessageContaining("already belongs to a workspace");

    assertThat(store.saved).isNull();
  }

  @Test
  void rejectsBootstrapWhenCredentialIsNotABcryptHash() {
    RecordingStore store = new RecordingStore(false);

    assertThatThrownBy(
            () ->
                service(store)
                    .bootstrap(
                        new BootstrapWorkspaceCommand(
                            "vraj@example.com",
                            "Vraj",
                            "plaintext-password",
                            "Acme Hiring",
                            "acme-hiring",
                            "UTC")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("passwordHash must be a BCrypt hash");

    assertThat(store.saved).isNull();
  }

  private WorkspaceBootstrapService service(RecordingStore store) {
    Deque<UUID> ids = new ArrayDeque<>();
    ids.add(uuid(1));
    ids.add(uuid(2));
    ids.add(uuid(3));
    Supplier<UUID> idGenerator = ids::removeFirst;
    return new WorkspaceBootstrapService(store, idGenerator, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static UUID uuid(long value) {
    return new UUID(0, value);
  }

  private static final class RecordingStore implements IdentityWorkspaceBootstrapStore {
    private final boolean hasMembership;
    private WorkspaceBootstrap saved;

    private RecordingStore(boolean hasMembership) {
      this.hasMembership = hasMembership;
    }

    @Override
    public boolean hasMembershipByNormalizedEmail(String normalizedEmail) {
      assertThat(normalizedEmail).isEqualTo("vraj@example.com");
      return hasMembership;
    }

    @Override
    public void save(WorkspaceBootstrap workspaceBootstrap) {
      this.saved = workspaceBootstrap;
    }
  }
}
