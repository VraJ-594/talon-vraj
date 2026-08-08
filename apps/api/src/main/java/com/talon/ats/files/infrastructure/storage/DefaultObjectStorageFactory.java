package com.talon.ats.files.infrastructure.storage;

import com.talon.ats.files.application.ObjectStorage;
import com.talon.ats.files.application.ObjectStorageFactory;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class DefaultObjectStorageFactory implements ObjectStorageFactory {

  @Override
  public ObjectStorage local(Path root) {
    return new LocalObjectStorage(root);
  }
}
