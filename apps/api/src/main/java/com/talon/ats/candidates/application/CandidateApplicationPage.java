package com.talon.ats.candidates.application;

import java.util.List;

public record CandidateApplicationPage(List<CandidateApplicationSummary> items, String nextCursor) {

  public CandidateApplicationPage {
    items = List.copyOf(items);
  }
}
