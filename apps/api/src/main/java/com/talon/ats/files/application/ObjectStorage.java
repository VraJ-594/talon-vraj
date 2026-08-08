package com.talon.ats.files.application;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;

public interface ObjectStorage {

  StoredObject put(PrivateObjectKey key, InputStream input, long maximumBytes);

  InputStream open(PrivateObjectKey key);

  boolean exists(PrivateObjectKey key);

  void delete(PrivateObjectKey key);

  void promote(PrivateObjectKey quarantine, PrivateObjectKey clean);

  default Optional<URI> presignCleanDownload(
      PrivateObjectKey clean, Duration lifetime, String downloadFileName) {
    return Optional.empty();
  }
}
