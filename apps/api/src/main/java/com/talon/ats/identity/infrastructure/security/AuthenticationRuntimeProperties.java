package com.talon.ats.identity.infrastructure.security;

import java.time.Duration;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("talon.security")
record AuthenticationRuntimeProperties(
    String issuer,
    String audience,
    String accessSigningKey,
    String refreshHashKey,
    Duration accessTokenLifetime,
    Duration refreshTokenLifetime) {

  AuthenticationRuntimeProperties {
    issuer = required(issuer, "issuer");
    audience = required(audience, "audience");
    accessSigningKey = required(accessSigningKey, "access-signing-key");
    refreshHashKey = required(refreshHashKey, "refresh-hash-key");
    accessTokenLifetime =
        positiveOrDefault(accessTokenLifetime, Duration.ofMinutes(15), "access-token-lifetime");
    refreshTokenLifetime =
        positiveOrDefault(refreshTokenLifetime, Duration.ofDays(7), "refresh-token-lifetime");
    decodeKey(accessSigningKey, "access-signing-key");
    decodeKey(refreshHashKey, "refresh-hash-key");
  }

  SecretKey accessSigningSecret() {
    return decodeKey(accessSigningKey, "access-signing-key");
  }

  SecretKey refreshHashSecret() {
    return decodeKey(refreshHashKey, "refresh-hash-key");
  }

  private static SecretKey decodeKey(String encoded, String field) {
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(encoded);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException(field + " must be valid Base64", exception);
    }
    if (bytes.length < 32) {
      throw new IllegalArgumentException(field + " must contain at least 256 bits");
    }
    return new SecretKeySpec(bytes, "HmacSHA256");
  }

  private static String required(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return value.trim();
  }

  private static Duration positiveOrDefault(Duration value, Duration fallback, String field) {
    Duration resolved = value == null ? fallback : value;
    if (resolved.isZero() || resolved.isNegative()) {
      throw new IllegalArgumentException(field + " must be positive");
    }
    return resolved;
  }
}
