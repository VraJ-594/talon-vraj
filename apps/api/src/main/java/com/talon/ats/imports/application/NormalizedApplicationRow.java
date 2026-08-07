package com.talon.ats.imports.application;

import java.time.LocalDate;

public record NormalizedApplicationRow(
    int rowNumber,
    String firstName,
    String lastName,
    String email,
    String resumeDriveUrl,
    Integer experienceMonths,
    Integer noticeDays,
    LocalDate applicationDate,
    NormalizedMoney currentCompensation,
    NormalizedMoney expectedCompensation) {}
