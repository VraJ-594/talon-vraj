package com.talon.ats.files.application;

import java.net.URI;
import java.util.Objects;

public record SourceReference(URI uri) {

  public SourceReference {
    Objects.requireNonNull(uri, "uri is required");
  }
}
