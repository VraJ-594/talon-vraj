package com.talon.ats.files.application;

import java.nio.file.Path;

/**
 * Creates application-owned private object-storage adapters without exposing provider internals.
 */
public interface ObjectStorageFactory {

  ObjectStorage local(Path root);

  default ObjectStorage s3(String bucket, String region) {
    throw new UnsupportedOperationException("S3 private storage is unavailable");
  }
}
