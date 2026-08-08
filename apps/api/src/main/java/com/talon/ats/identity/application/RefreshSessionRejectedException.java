package com.talon.ats.identity.application;

public final class RefreshSessionRejectedException extends RuntimeException {

  public RefreshSessionRejectedException() {
    super("Session is unavailable");
  }
}
