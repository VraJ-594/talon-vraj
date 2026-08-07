package com.talon.ats.files.application;

import java.time.Duration;
import java.util.Objects;

public record FetchPolicy(
    long maximumBytes, int maximumRedirects, Duration responseTimeout, Duration totalTimeout) {

  private static final long TEN_MEGABYTES = 10L * 1024 * 1024;

  public FetchPolicy {
    if (maximumBytes < 5) {
      throw new IllegalArgumentException("maximumBytes must fit a PDF signature");
    }
    if (maximumRedirects < 0 || maximumRedirects > 10) {
      throw new IllegalArgumentException("maximumRedirects must be between 0 and 10");
    }
    Objects.requireNonNull(responseTimeout, "responseTimeout is required");
    Objects.requireNonNull(totalTimeout, "totalTimeout is required");
    if (responseTimeout.isZero() || responseTimeout.isNegative()) {
      throw new IllegalArgumentException("responseTimeout must be positive");
    }
    if (totalTimeout.isZero() || totalTimeout.isNegative()) {
      throw new IllegalArgumentException("totalTimeout must be positive");
    }
    if (totalTimeout.compareTo(responseTimeout) < 0) {
      throw new IllegalArgumentException("totalTimeout must not be shorter than responseTimeout");
    }
  }

  public static FetchPolicy publicDriveDefaults() {
    return new FetchPolicy(TEN_MEGABYTES, 5, Duration.ofSeconds(15), Duration.ofSeconds(30));
  }
}
