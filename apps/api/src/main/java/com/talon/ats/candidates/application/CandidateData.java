package com.talon.ats.candidates.application;

import java.util.Locale;

public record CandidateData(
    String email,
    String firstName,
    String lastName,
    String phone,
    String location,
    String currentTitle,
    String currentCompany,
    String skills,
    Integer experienceMonths) {

  public CandidateData {
    email = required(email, "email").toLowerCase(Locale.ROOT);
    firstName = required(firstName, "firstName");
    lastName = required(lastName, "lastName");
    phone = optional(phone);
    location = optional(location);
    currentTitle = optional(currentTitle);
    currentCompany = optional(currentCompany);
    skills = optional(skills);
    if (experienceMonths != null && experienceMonths < 0) {
      throw new IllegalArgumentException("experienceMonths must not be negative");
    }
  }

  private static String required(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return value.trim();
  }

  private static String optional(String value) {
    return value == null ? null : value.trim();
  }
}
