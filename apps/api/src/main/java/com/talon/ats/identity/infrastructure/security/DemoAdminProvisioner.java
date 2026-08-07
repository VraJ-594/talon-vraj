package com.talon.ats.identity.infrastructure.security;

import com.talon.ats.identity.application.BootstrapWorkspaceCommand;
import com.talon.ats.identity.application.WorkspaceBootstrapNotAllowedException;
import com.talon.ats.identity.application.WorkspaceBootstrapService;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

public final class DemoAdminProvisioner implements ApplicationRunner {

  private static final Logger LOGGER = LoggerFactory.getLogger(DemoAdminProvisioner.class);

  private final WorkspaceBootstrapService bootstrapService;
  private final BootstrapWorkspaceCommand command;

  public DemoAdminProvisioner(
      WorkspaceBootstrapService bootstrapService, BootstrapWorkspaceCommand command) {
    this.bootstrapService = Objects.requireNonNull(bootstrapService);
    this.command = Objects.requireNonNull(command);
  }

  @Override
  public void run(ApplicationArguments arguments) {
    try {
      bootstrapService.bootstrap(command);
      LOGGER.info("Demo administrator workspace provisioned");
    } catch (WorkspaceBootstrapNotAllowedException alreadyProvisioned) {
      LOGGER.info("Demo administrator workspace already provisioned");
    }
  }
}
