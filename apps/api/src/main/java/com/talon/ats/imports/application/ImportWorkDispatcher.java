package com.talon.ats.imports.application;

import java.util.UUID;

@FunctionalInterface
public interface ImportWorkDispatcher {

  void dispatch(ImportDraftService.Actor actor, UUID importId);
}
