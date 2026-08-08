package com.talon.ats.search.application;

public final class SearchInterpreterException extends RuntimeException {

  private final String code;

  public SearchInterpreterException(String code, String message) {
    super(message);
    this.code = code;
  }

  public SearchInterpreterException(String code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
