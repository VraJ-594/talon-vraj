package com.talon.ats.files.application;

public record ExternalFileMetadata(long sizeBytes, String contentType) {

  public ExternalFileMetadata {
    if (sizeBytes < 0) {
      throw new IllegalArgumentException("sizeBytes must not be negative");
    }
    if (contentType == null || contentType.isBlank()) {
      throw new IllegalArgumentException("contentType is required");
    }
  }
}
