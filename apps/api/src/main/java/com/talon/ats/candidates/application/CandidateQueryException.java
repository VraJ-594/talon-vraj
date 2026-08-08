package com.talon.ats.candidates.application;

public final class CandidateQueryException extends RuntimeException {

  private final String code;

  public CandidateQueryException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
