package com.talon.ats.imports.application;

import java.util.Objects;

public final class ImportProblem extends RuntimeException {

  private final String code;

  public ImportProblem(String code, String message) {
    super(message);
    this.code = Objects.requireNonNull(code, "code is required");
  }

  public ImportProblem(String code, String message, Throwable cause) {
    super(message, cause);
    this.code = Objects.requireNonNull(code, "code is required");
  }

  public String code() {
    return code;
  }
}
