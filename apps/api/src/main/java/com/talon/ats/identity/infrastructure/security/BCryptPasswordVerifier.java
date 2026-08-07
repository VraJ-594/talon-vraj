package com.talon.ats.identity.infrastructure.security;

import com.talon.ats.identity.application.PasswordVerifier;
import java.util.Objects;
import org.springframework.security.crypto.password.PasswordEncoder;

public final class BCryptPasswordVerifier implements PasswordVerifier {

  private final PasswordEncoder passwordEncoder;

  public BCryptPasswordVerifier(PasswordEncoder passwordEncoder) {
    this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
  }

  @Override
  public boolean matches(String rawPassword, String encodedPassword) {
    return passwordEncoder.matches(rawPassword, encodedPassword);
  }
}
