package com.talon.ats.candidates.application;

import com.talon.ats.files.application.PrivateObjectKey;
import java.util.Objects;
import java.util.UUID;

public record CandidateResume(
    UUID workspaceId, String fileName, String contentType, PrivateObjectKey objectKey) {

  public CandidateResume {
    Objects.requireNonNull(workspaceId, "workspaceId is required");
    Objects.requireNonNull(fileName, "fileName is required");
    Objects.requireNonNull(contentType, "contentType is required");
    Objects.requireNonNull(objectKey, "objectKey is required");
    if (!objectKey.isCleanResume()) {
      throw new IllegalArgumentException("candidate resume must use a clean object key");
    }
  }
}
