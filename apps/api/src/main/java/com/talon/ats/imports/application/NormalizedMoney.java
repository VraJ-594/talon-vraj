package com.talon.ats.imports.application;

import java.util.Objects;

public record NormalizedMoney(String currency, long minorUnits) {
  public NormalizedMoney {
    Objects.requireNonNull(currency, "currency is required");
    if (minorUnits < 0) {
      throw new IllegalArgumentException("minorUnits must not be negative");
    }
  }
}
