package com.talon.ats.files.application;

import java.util.Objects;

public record ExtractedResumeText(String text, int pageCount, boolean truncated) {

  public static final int MAXIMUM_PAGES = 50;
  public static final int MAXIMUM_CHARACTERS = 500_000;

  public ExtractedResumeText {
    Objects.requireNonNull(text, "text is required");
    if (pageCount < 0 || pageCount > MAXIMUM_PAGES) {
      throw new IllegalArgumentException("pageCount exceeds the extraction contract");
    }
    if (text.length() > MAXIMUM_CHARACTERS) {
      throw new IllegalArgumentException("text exceeds the extraction contract");
    }
  }
}
