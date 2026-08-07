package com.talon.ats.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.talon.ats.identity.application.BootstrapWorkspaceCommand;
import com.talon.ats.identity.application.IdentityWorkspaceBootstrapStore;
import com.talon.ats.identity.application.WorkspaceBootstrapService;
import com.talon.ats.identity.domain.WorkspaceBootstrap;
import com.talon.ats.identity.infrastructure.security.DemoAdminProvisioner;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class DemoAdminProvisionerTests {

  private static final String PASSWORD_HASH =
      "$2a$12$WjJ0PmyFD6h7xjNQxJV13u98Ot4GY9NaAcNn0me04X6NxoJ/Oc9cW";

  @Test
  void provisionsConfiguredAdministratorWithoutLoggingOrPersistingPlaintext() throws Exception {
    RecordingStore store = new RecordingStore(false);
    DemoAdminProvisioner provisioner = provisioner(store);

    provisioner.run(new DefaultApplicationArguments());

    assertThat(store.saved).isNotNull();
    assertThat(store.saved.user().normalizedEmail()).isEqualTo("admin@talon.example");
    assertThat(store.saved.user().passwordHash()).isEqualTo(PASSWORD_HASH);
    assertThat(store.saved.workspace().slug()).isEqualTo("talon-demo");
  }

  @Test
  void treatsAnExistingAdministratorAsAnIdempotentStartup() throws Exception {
    RecordingStore store = new RecordingStore(true);

    provisioner(store).run(new DefaultApplicationArguments());

    assertThat(store.saved).isNull();
  }

  private static DemoAdminProvisioner provisioner(RecordingStore store) {
    WorkspaceBootstrapService service =
        new WorkspaceBootstrapService(
            store,
            UUID::randomUUID,
            Clock.fixed(Instant.parse("2026-08-07T10:00:00Z"), ZoneOffset.UTC));
    return new DemoAdminProvisioner(
        service,
        new BootstrapWorkspaceCommand(
            "admin@talon.example",
            "Demo Administrator",
            PASSWORD_HASH,
            "Talon Demo",
            "talon-demo",
            "Asia/Kolkata"));
  }

  private static final class RecordingStore implements IdentityWorkspaceBootstrapStore {
    private final boolean exists;
    private WorkspaceBootstrap saved;

    private RecordingStore(boolean exists) {
      this.exists = exists;
    }

    @Override
    public boolean hasMembershipByNormalizedEmail(String normalizedEmail) {
      return exists;
    }

    @Override
    public void save(WorkspaceBootstrap workspaceBootstrap) {
      saved = workspaceBootstrap;
    }
  }
}
