package com.talon.ats.files.application;

public record StoredObject(PrivateObjectKey key, long sizeBytes, String sha256Hex) {

  public StoredObject {
    if (key == null || sizeBytes < 0 || sha256Hex == null || !sha256Hex.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("stored object metadata is invalid");
    }
  }
}
