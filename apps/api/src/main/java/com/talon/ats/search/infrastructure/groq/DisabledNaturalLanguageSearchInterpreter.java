package com.talon.ats.search.infrastructure.groq;

import com.talon.ats.search.application.NaturalLanguageQuery;
import com.talon.ats.search.application.NaturalLanguageSearchInterpreter;
import com.talon.ats.search.application.SearchInterpreterException;
import com.talon.ats.search.domain.CandidateSearchCriteria;

public final class DisabledNaturalLanguageSearchInterpreter
    implements NaturalLanguageSearchInterpreter {

  @Override
  public CandidateSearchCriteria interpret(NaturalLanguageQuery query) {
    throw new SearchInterpreterException(
        "INTERPRETER_DISABLED", "AI interpretation is not configured");
  }
}
