package com.talon.ats.files.application;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

public final class ResumeTransferService {

  private static final long MAXIMUM_PDF_BYTES = 10L * 1024 * 1024;

  private final ExternalFileSource source;
  private final ObjectStorage storage;

  public ResumeTransferService(ExternalFileSource source, ObjectStorage storage) {
    this.source = Objects.requireNonNull(source);
    this.storage = Objects.requireNonNull(storage);
  }

  public TransferResult transfer(UUID workspaceId, UUID fileId, UUID versionId, URI sourceUri) {
    PrivateObjectKey quarantine = PrivateObjectKey.quarantineResume(workspaceId, fileId, versionId);
    try {
      ExternalFileMetadata metadata =
          source.fetch(
              new SourceReference(sourceUri),
              (input, maximumBytes) ->
                  storage
                      .put(quarantine, input, Math.min(MAXIMUM_PDF_BYTES, maximumBytes))
                      .sizeBytes());
      return new TransferResult(fileId, quarantine, metadata.sizeBytes(), metadata.contentType());
    } catch (RuntimeException failure) {
      storage.delete(quarantine);
      throw failure;
    }
  }

  public record TransferResult(
      UUID fileId, PrivateObjectKey objectKey, long sizeBytes, String contentType) {}
}
