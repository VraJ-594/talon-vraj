package com.talon.ats.imports.domain;

public enum ImportStatus {
  UPLOADED,
  MAPPED,
  VALIDATING,
  PREVIEW_READY,
  CONFIRMED,
  PROCESSING,
  COMPLETED,
  COMPLETED_WITH_ERRORS,
  FAILED,
  CANCELLED
}
