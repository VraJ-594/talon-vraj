package com.talon.ats.identity.application;

public final class AuthenticationFailedException extends RuntimeException {

  public AuthenticationFailedException() {
    super("Invalid email or password");
  }
}
