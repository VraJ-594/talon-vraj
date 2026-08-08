package com.talon.ats.candidates.application;

import java.util.List;

public record CandidateApplicationSlice(
    List<CandidateApplicationSummary> items, CandidateApplicationCursor nextCursor) {

  public CandidateApplicationSlice {
    items = List.copyOf(items);
  }
}
