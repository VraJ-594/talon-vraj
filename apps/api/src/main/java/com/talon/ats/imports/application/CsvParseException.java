package com.talon.ats.imports.application;

public final class CsvParseException extends RuntimeException {

  private final String code;

  public CsvParseException(String code, String message) {
    super(message);
    this.code = code;
  }

  public CsvParseException(String code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
