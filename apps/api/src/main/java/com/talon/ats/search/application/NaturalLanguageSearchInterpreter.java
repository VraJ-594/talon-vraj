package com.talon.ats.search.application;

import com.talon.ats.search.domain.CandidateSearchCriteria;

@FunctionalInterface
public interface NaturalLanguageSearchInterpreter {

  CandidateSearchCriteria interpret(NaturalLanguageQuery query);
}
