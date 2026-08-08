package com.talon.ats.imports.application;

import java.time.LocalDate;

public record NormalizedApplicationRow(
    int rowNumber,
    String firstName,
    String lastName,
    String email,
    String resumeDriveUrl,
    String phone,
    String location,
    String currentCompany,
    String currentTitle,
    String skills,
    Integer experienceMonths,
    Integer noticeDays,
    LocalDate availabilityDate,
    LocalDate applicationDate,
    String source,
    NormalizedMoney currentCompensation,
    NormalizedMoney expectedCompensation) {

  public NormalizedApplicationRow(
      int rowNumber,
      String firstName,
      String lastName,
      String email,
      String resumeDriveUrl,
      Integer experienceMonths,
      Integer noticeDays,
      LocalDate applicationDate,
      NormalizedMoney currentCompensation,
      NormalizedMoney expectedCompensation) {
    this(
        rowNumber,
        firstName,
        lastName,
        email,
        resumeDriveUrl,
        null,
        null,
        null,
        null,
        null,
        experienceMonths,
        noticeDays,
        null,
        applicationDate,
        "CSV_IMPORT",
        currentCompensation,
        expectedCompensation);
  }
}
