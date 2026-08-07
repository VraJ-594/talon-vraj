package com.talon.ats.imports.application;

import com.talon.ats.imports.domain.CanonicalField;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class CanonicalRowValidator {

  private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
  private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);
  private static final BigDecimal PAISE_PER_LAKH = BigDecimal.valueOf(10_000_000);

  public RowValidationResult validate(int rowNumber, Map<CanonicalField, String> values) {
    if (rowNumber < 2) {
      throw new IllegalArgumentException("rowNumber must identify a data row");
    }
    Map<CanonicalField, String> safeValues = values == null ? Map.of() : values;
    List<RowValidationError> errors = new ArrayList<>();
    String firstName = required(safeValues, CanonicalField.FIRST_NAME, errors);
    String lastName = required(safeValues, CanonicalField.LAST_NAME, errors);
    String email = required(safeValues, CanonicalField.EMAIL, errors).toLowerCase(Locale.ROOT);
    String resumeUrl = required(safeValues, CanonicalField.RESUME_DRIVE_URL, errors);
    if (!email.isBlank() && !EMAIL_PATTERN.matcher(email).matches()) {
      errors.add(error(CanonicalField.EMAIL, "INVALID_EMAIL", "email format is invalid"));
    }

    Integer experienceMonths = experience(safeValues, errors);
    Integer noticeDays =
        nonNegativeInteger(safeValues, CanonicalField.NOTICE_PERIOD_DAYS, "INVALID_NOTICE", errors);
    LocalDate applicationDate = date(safeValues, CanonicalField.APPLICATION_DATE, errors);
    NormalizedMoney current = money(safeValues, CanonicalField.CURRENT_CTC, errors);
    NormalizedMoney expected = money(safeValues, CanonicalField.EXPECTED_CTC, errors);

    if (!errors.isEmpty()) {
      return new RowValidationResult(rowNumber, null, errors);
    }
    return new RowValidationResult(
        rowNumber,
        new NormalizedApplicationRow(
            rowNumber,
            firstName,
            lastName,
            email,
            resumeUrl,
            experienceMonths,
            noticeDays,
            applicationDate,
            current,
            expected),
        List.of());
  }

  private static String required(
      Map<CanonicalField, String> values, CanonicalField field, List<RowValidationError> errors) {
    String value = value(values, field);
    if (value.isBlank()) {
      errors.add(error(field, "REQUIRED_VALUE_MISSING", field.name() + " is required"));
    }
    return value;
  }

  private static Integer experience(
      Map<CanonicalField, String> values, List<RowValidationError> errors) {
    String value = value(values, CanonicalField.TOTAL_EXPERIENCE_YEARS);
    if (value.isBlank()) {
      return null;
    }
    try {
      BigDecimal months = new BigDecimal(value).multiply(MONTHS_PER_YEAR);
      int result = months.setScale(0, RoundingMode.UNNECESSARY).intValueExact();
      if (result < 0) {
        throw new ArithmeticException("negative");
      }
      return result;
    } catch (ArithmeticException | NumberFormatException exception) {
      errors.add(
          error(
              CanonicalField.TOTAL_EXPERIENCE_YEARS,
              "INVALID_EXPERIENCE",
              "experience must be a non-negative number of years"));
      return null;
    }
  }

  private static Integer nonNegativeInteger(
      Map<CanonicalField, String> values,
      CanonicalField field,
      String code,
      List<RowValidationError> errors) {
    String value = value(values, field);
    if (value.isBlank()) {
      return null;
    }
    try {
      int parsed = Integer.parseInt(value);
      if (parsed < 0) {
        throw new NumberFormatException("negative");
      }
      return parsed;
    } catch (NumberFormatException exception) {
      errors.add(error(field, code, field.name() + " must be a non-negative integer"));
      return null;
    }
  }

  private static LocalDate date(
      Map<CanonicalField, String> values, CanonicalField field, List<RowValidationError> errors) {
    String value = value(values, field);
    if (value.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException exception) {
      errors.add(error(field, "INVALID_DATE", field.name() + " must use ISO date format"));
      return null;
    }
  }

  private static NormalizedMoney money(
      Map<CanonicalField, String> values,
      CanonicalField amountField,
      List<RowValidationError> errors) {
    String amountValue = value(values, amountField);
    if (amountValue.isBlank()) {
      return null;
    }
    String unit = value(values, CanonicalField.CTC_UNIT).toUpperCase(Locale.ROOT);
    String currencyCode = value(values, CanonicalField.CTC_CURRENCY).toUpperCase(Locale.ROOT);
    try {
      BigDecimal amount = new BigDecimal(amountValue);
      if (amount.signum() < 0) {
        throw new ArithmeticException("negative");
      }
      long minorUnits;
      if ("LPA".equals(unit)) {
        if (!"INR".equals(currencyCode)) {
          throw new IllegalArgumentException("LPA requires INR");
        }
        minorUnits = amount.multiply(PAISE_PER_LAKH).longValueExact();
      } else if ("ANNUAL".equals(unit)) {
        Currency currency = Currency.getInstance(currencyCode);
        minorUnits = amount.movePointRight(currency.getDefaultFractionDigits()).longValueExact();
      } else {
        throw new IllegalArgumentException("unsupported unit");
      }
      return new NormalizedMoney(currencyCode, minorUnits);
    } catch (ArithmeticException | IllegalArgumentException exception) {
      errors.add(
          error(amountField, "INVALID_MONEY", "compensation value, unit, or currency is invalid"));
      return null;
    }
  }

  private static String value(Map<CanonicalField, String> values, CanonicalField field) {
    String value = values.get(field);
    return value == null ? "" : value.trim();
  }

  private static RowValidationError error(CanonicalField field, String code, String message) {
    return new RowValidationError(field, code, message);
  }
}
