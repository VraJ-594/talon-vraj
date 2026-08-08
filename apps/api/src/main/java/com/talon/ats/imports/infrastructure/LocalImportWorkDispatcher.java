package com.talon.ats.imports.infrastructure;

import com.talon.ats.imports.application.ImportApplicationWorker;
import com.talon.ats.imports.application.ImportDraftService;
import com.talon.ats.imports.application.ImportWorkDispatcher;
import java.util.Objects;
import java.util.UUID;
import org.springframework.core.task.TaskExecutor;

public final class LocalImportWorkDispatcher implements ImportWorkDispatcher {

  private final TaskExecutor executor;
  private final ImportApplicationWorker worker;

  public LocalImportWorkDispatcher(TaskExecutor executor, ImportApplicationWorker worker) {
    this.executor = Objects.requireNonNull(executor);
    this.worker = Objects.requireNonNull(worker);
  }

  @Override
  public void dispatch(ImportDraftService.Actor actor, UUID importId) {
    executor.execute(() -> worker.process(actor, importId));
  }
}
