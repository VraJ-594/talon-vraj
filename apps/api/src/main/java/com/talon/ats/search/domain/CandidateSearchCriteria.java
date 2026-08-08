package com.talon.ats.search.domain;

import java.util.List;

public record CandidateSearchCriteria(
    String dslVersion,
    String text,
    List<SearchPredicate> predicates,
    SearchSort sort,
    Integer limit,
    String cursor) {

  public CandidateSearchCriteria {
    predicates = predicates == null ? List.of() : List.copyOf(predicates);
  }
}
