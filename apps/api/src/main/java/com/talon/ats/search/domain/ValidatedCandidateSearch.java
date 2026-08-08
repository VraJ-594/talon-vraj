package com.talon.ats.search.domain;

import java.util.List;

public record ValidatedCandidateSearch(
    String text,
    List<ValidatedSearchPredicate> predicates,
    SearchSort sort,
    int limit,
    SearchCursor cursor) {

  public ValidatedCandidateSearch {
    predicates = List.copyOf(predicates);
  }
}
