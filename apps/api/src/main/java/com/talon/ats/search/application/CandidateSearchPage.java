package com.talon.ats.search.application;

import java.util.List;

public record CandidateSearchPage(List<CandidateSearchResult> results, String nextCursor) {

  public CandidateSearchPage {
    results = List.copyOf(results);
  }
}
