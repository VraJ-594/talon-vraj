package com.talon.ats.imports.domain;

public enum CanonicalField {
  FIRST_NAME(true),
  LAST_NAME(true),
  EMAIL(true),
  RESUME_DRIVE_URL(true),
  PHONE(false),
  LOCATION(false),
  TOTAL_EXPERIENCE_YEARS(false),
  CURRENT_COMPANY(false),
  CURRENT_TITLE(false),
  SKILLS(false),
  CURRENT_CTC(false),
  EXPECTED_CTC(false),
  CTC_UNIT(false),
  CTC_CURRENCY(false),
  NOTICE_PERIOD_DAYS(false),
  AVAILABILITY_DATE(false),
  SOURCE(false),
  APPLICATION_DATE(false);

  private final boolean required;

  CanonicalField(boolean required) {
    this.required = required;
  }

  public boolean required() {
    return required;
  }
}
