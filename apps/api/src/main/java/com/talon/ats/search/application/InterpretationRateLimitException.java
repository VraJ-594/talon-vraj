package com.talon.ats.search.application;

public final class InterpretationRateLimitException extends RuntimeException {

  public InterpretationRateLimitException() {
    super("Natural-language search rate limit exceeded");
  }
}
