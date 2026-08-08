package com.talon.ats.search.application;

import com.talon.ats.search.domain.SearchCursor;
import java.util.List;

public record SearchResultSlice(List<CandidateSearchResult> results, SearchCursor nextCursor) {

  public SearchResultSlice {
    results = List.copyOf(results);
  }
}
