package com.talon.ats.search.application;

public final class SearchValidationException extends RuntimeException {

  private final String code;

  public SearchValidationException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
