package com.talon.ats;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModuleArchitectureTests {

  @Test
  void domainModulesRespectDeclaredBoundaries() {
    ApplicationModules.of(TalonAtsApplication.class).verify();
  }
}
