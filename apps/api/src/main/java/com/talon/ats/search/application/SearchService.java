package com.talon.ats.search.application;

import com.talon.ats.search.domain.CandidateSearchCriteria;
import com.talon.ats.search.domain.SearchPredicate;
import com.talon.ats.search.domain.ValidatedCandidateSearch;
import com.talon.ats.search.domain.ValidatedSearchPredicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SearchService {

  private static final int MAX_NATURAL_QUERY_LENGTH = 500;
  private final SearchDslValidator validator;
  private final CandidateSearchStore store;
  private final NaturalLanguageSearchInterpreter interpreter;
  private final InterpretationRateLimiter rateLimiter;

  public SearchService(
      SearchDslValidator validator,
      CandidateSearchStore store,
      NaturalLanguageSearchInterpreter interpreter,
      InterpretationRateLimiter rateLimiter) {
    this.validator = validator;
    this.store = store;
    this.interpreter = interpreter;
    this.rateLimiter = rateLimiter;
  }

  public List<CommandSearchItem> command(SearchActor actor, String query, int limit) {
    String normalized = query == null ? "" : query.trim();
    if (normalized.length() < 2 || normalized.length() > 100) {
      throw new SearchValidationException(
          "COMMAND_QUERY_INVALID", "Command search requires 2 to 100 characters");
    }
    if (limit < 1 || limit > 20) {
      throw new SearchValidationException(
          "COMMAND_LIMIT_INVALID", "Command search limit must be between 1 and 20");
    }
    return store.command(actor.workspaceId(), normalized, limit);
  }

  public InterpretedCandidateSearch interpret(
      SearchActor actor, NaturalLanguageQuery naturalQuery) {
    String query =
        naturalQuery == null || naturalQuery.query() == null ? "" : naturalQuery.query().trim();
    if (query.isEmpty() || query.length() > MAX_NATURAL_QUERY_LENGTH) {
      throw new SearchValidationException(
          "NATURAL_QUERY_INVALID", "Natural-language query must contain 1 to 500 characters");
    }
    rateLimiter.acquire(actor.userId());
    CandidateSearchCriteria untrusted =
        interpreter.interpret(
            new NaturalLanguageQuery(query, naturalQuery.locale(), naturalQuery.timezone()));
    ValidatedCandidateSearch validated = validator.validate(untrusted, actor.role());
    CandidateSearchCriteria canonical = canonical(validated);
    List<SearchFilterChip> chips =
        validated.predicates().stream().map(SearchService::chip).toList();
    List<String> warnings = new ArrayList<>();
    boolean hasInrCompensation =
        validated.predicates().stream()
            .anyMatch(
                predicate ->
                    predicate.currency() != null && predicate.currency().equalsIgnoreCase("INR"));
    if (hasInrCompensation) {
      warnings.add("LPA was interpreted as annual INR; no currency conversion was applied.");
    }
    return new InterpretedCandidateSearch(canonical, chips, warnings);
  }

  public CandidateSearchPage query(SearchActor actor, CandidateSearchCriteria criteria) {
    ValidatedCandidateSearch validated = validator.validate(criteria, actor.role());
    SearchResultSlice slice = store.search(actor.workspaceId(), validated);
    return new CandidateSearchPage(slice.results(), validator.encodeCursor(slice.nextCursor()));
  }

  private static CandidateSearchCriteria canonical(ValidatedCandidateSearch search) {
    List<SearchPredicate> predicates =
        search.predicates().stream()
            .map(
                predicate ->
                    new SearchPredicate(
                        predicate.field(),
                        predicate.operator(),
                        value(predicate),
                        predicate.currency()))
            .toList();
    return new CandidateSearchCriteria(
        SearchDslValidator.DSL_VERSION,
        search.text(),
        predicates,
        search.sort(),
        search.limit(),
        null);
  }

  private static SearchFilterChip chip(ValidatedSearchPredicate predicate) {
    String value = value(predicate);
    if (predicate.currency() != null) {
      value = compensationLabel(predicate.currency(), predicate.numberValue());
    }
    return new SearchFilterChip(
        predicate.field(),
        predicate.operator(),
        fieldLabel(predicate.field()) + " " + operatorLabel(predicate.operator()),
        value);
  }

  private static String value(ValidatedSearchPredicate predicate) {
    if (predicate.textValue() != null) {
      return predicate.textValue();
    }
    if (predicate.dateValue() != null) {
      return predicate.dateValue().toString();
    }
    return Long.toString(predicate.numberValue());
  }

  private static String compensationLabel(String currency, long minorUnits) {
    if ("INR".equals(currency)) {
      double lpa = minorUnits / 100.0d / 100_000.0d;
      return String.format(Locale.ROOT, "%.2f LPA", lpa);
    }
    return currency + " " + String.format(Locale.ROOT, "%.2f", minorUnits / 100.0d);
  }

  private static String fieldLabel(com.talon.ats.search.domain.SearchField field) {
    return switch (field) {
      case LOCATION -> "Location";
      case CURRENT_TITLE -> "Current title";
      case CURRENT_COMPANY -> "Current company";
      case SKILLS -> "Skills";
      case APPLICATION_STAGE -> "Stage";
      case JOB_TITLE -> "Job";
      case SOURCE -> "Source";
      case EXPERIENCE_MONTHS -> "Experience (months)";
      case NOTICE_PERIOD_DAYS -> "Notice period (days)";
      case APPLIED_AT -> "Applied date";
      case AVAILABLE_FROM -> "Available date";
      case CURRENT_COMPENSATION -> "Current compensation";
      case EXPECTED_COMPENSATION -> "Expected compensation";
    };
  }

  private static String operatorLabel(com.talon.ats.search.domain.SearchOperator operator) {
    return switch (operator) {
      case EQUALS, ON -> "is";
      case CONTAINS -> "contains";
      case GREATER_THAN -> "more than";
      case GREATER_THAN_OR_EQUAL -> "at least";
      case LESS_THAN -> "less than";
      case LESS_THAN_OR_EQUAL -> "at most";
      case BEFORE -> "before";
      case AFTER -> "after";
    };
  }
}
