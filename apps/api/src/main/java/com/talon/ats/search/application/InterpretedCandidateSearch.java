package com.talon.ats.search.application;

import com.talon.ats.search.domain.CandidateSearchCriteria;
import java.util.List;

public record InterpretedCandidateSearch(
    CandidateSearchCriteria criteria, List<SearchFilterChip> chips, List<String> warnings) {

  public InterpretedCandidateSearch {
    chips = List.copyOf(chips);
    warnings = List.copyOf(warnings);
  }
}
