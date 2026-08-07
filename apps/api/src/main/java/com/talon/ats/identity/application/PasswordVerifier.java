package com.talon.ats.identity.application;

public interface PasswordVerifier {

  boolean matches(String rawPassword, String encodedPassword);
}
