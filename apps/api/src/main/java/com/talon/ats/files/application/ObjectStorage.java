package com.talon.ats.files.application;

import java.io.InputStream;

public interface ObjectStorage {

  StoredObject put(PrivateObjectKey key, InputStream input, long maximumBytes);

  InputStream open(PrivateObjectKey key);

  boolean exists(PrivateObjectKey key);

  void promote(PrivateObjectKey quarantine, PrivateObjectKey clean);
}
