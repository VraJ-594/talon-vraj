package com.talon.ats.jobs.contract;

import java.util.UUID;

public interface ImportTargetAccess {

  boolean isImportable(UUID workspaceId, UUID jobId);
}
