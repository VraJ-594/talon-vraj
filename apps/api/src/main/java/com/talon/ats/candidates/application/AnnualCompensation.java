package com.talon.ats.candidates.application;

import java.util.Locale;

public record AnnualCompensation(String currency, long minorUnits) {

  public AnnualCompensation {
    currency = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
    if (!currency.matches("[A-Z]{3}")) {
      throw new IllegalArgumentException("currency must be an ISO 4217 code");
    }
    if (minorUnits < 0) {
      throw new IllegalArgumentException("minorUnits must not be negative");
    }
  }
}
