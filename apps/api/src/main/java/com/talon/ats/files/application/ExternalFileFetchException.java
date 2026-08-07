package com.talon.ats.files.application;

import java.time.Duration;
import java.util.Optional;

public final class ExternalFileFetchException extends RuntimeException {

  private final String code;
  private final boolean retryable;
  private final Duration retryAfter;

  public ExternalFileFetchException(String code, String message, boolean retryable) {
    this(code, message, retryable, null, null);
  }

  public ExternalFileFetchException(
      String code, String message, boolean retryable, Duration retryAfter) {
    this(code, message, retryable, retryAfter, null);
  }

  public ExternalFileFetchException(
      String code, String message, boolean retryable, Duration retryAfter, Throwable cause) {
    super(message, cause);
    this.code = code;
    this.retryable = retryable;
    this.retryAfter = retryAfter;
  }

  public String code() {
    return code;
  }

  public boolean retryable() {
    return retryable;
  }

  public Optional<Duration> retryAfter() {
    return Optional.ofNullable(retryAfter);
  }
}
