package com.talon.ats.files.application;

public final class FileProcessingException extends RuntimeException {

  private final String code;

  public FileProcessingException(String code, String message) {
    super(message);
    this.code = code;
  }

  public FileProcessingException(String code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
