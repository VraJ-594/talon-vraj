package com.talon.ats.search.application;

import com.talon.ats.identity.contract.WorkspaceRole;
import com.talon.ats.search.domain.CandidateSearchCriteria;
import com.talon.ats.search.domain.SearchCursor;
import com.talon.ats.search.domain.SearchField;
import com.talon.ats.search.domain.SearchOperator;
import com.talon.ats.search.domain.SearchPredicate;
import com.talon.ats.search.domain.SearchSort;
import com.talon.ats.search.domain.ValidatedCandidateSearch;
import com.talon.ats.search.domain.ValidatedSearchPredicate;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class SearchDslValidator {

  public static final String DSL_VERSION = "1";
  private static final int MAX_TEXT_LENGTH = 200;
  private static final int MAX_PREDICATES = 12;
  private static final int DEFAULT_LIMIT = 25;
  private static final int MAX_LIMIT = 100;
  private static final EnumSet<SearchField> TEXT_FIELDS =
      EnumSet.of(
          SearchField.LOCATION,
          SearchField.CURRENT_TITLE,
          SearchField.CURRENT_COMPANY,
          SearchField.SKILLS,
          SearchField.APPLICATION_STAGE,
          SearchField.JOB_TITLE,
          SearchField.SOURCE);
  private static final EnumSet<SearchField> NUMBER_FIELDS =
      EnumSet.of(SearchField.EXPERIENCE_MONTHS, SearchField.NOTICE_PERIOD_DAYS);
  private static final EnumSet<SearchField> DATE_FIELDS =
      EnumSet.of(SearchField.APPLIED_AT, SearchField.AVAILABLE_FROM);
  private static final EnumSet<SearchField> COMPENSATION_FIELDS =
      EnumSet.of(SearchField.CURRENT_COMPENSATION, SearchField.EXPECTED_COMPENSATION);

  public ValidatedCandidateSearch validate(CandidateSearchCriteria criteria, WorkspaceRole role) {
    if (criteria == null) {
      throw invalid("SEARCH_CRITERIA_REQUIRED", "Search criteria are required");
    }
    if (!DSL_VERSION.equals(criteria.dslVersion())) {
      throw invalid("DSL_VERSION_UNSUPPORTED", "Search DSL version is not supported");
    }
    String text = normalize(criteria.text());
    if (text != null && text.length() > MAX_TEXT_LENGTH) {
      throw invalid("SEARCH_TEXT_TOO_LONG", "Search text must be 200 characters or fewer");
    }
    if (criteria.predicates().size() > MAX_PREDICATES) {
      throw invalid("TOO_MANY_FILTERS", "A search can contain at most 12 filters");
    }
    List<ValidatedSearchPredicate> predicates =
        criteria.predicates().stream().map(predicate -> validate(predicate, role)).toList();
    SearchSort sort = criteria.sort() == null ? SearchSort.newestFirst() : criteria.sort();
    if (sort.field() == null || sort.direction() == null) {
      throw invalid("SORT_INVALID", "Search sort field and direction are required");
    }
    int limit = criteria.limit() == null ? DEFAULT_LIMIT : criteria.limit();
    if (limit < 1 || limit > MAX_LIMIT) {
      throw invalid("LIMIT_INVALID", "Search limit must be between 1 and 100");
    }
    return new ValidatedCandidateSearch(
        text, predicates, sort, limit, decodeCursor(criteria.cursor()));
  }

  public String encodeCursor(SearchCursor cursor) {
    if (cursor == null) {
      return null;
    }
    String raw = cursor.sortValue() + "\n" + cursor.applicationId();
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  private ValidatedSearchPredicate validate(SearchPredicate predicate, WorkspaceRole role) {
    if (predicate == null || predicate.field() == null || predicate.operator() == null) {
      throw invalid("FILTER_INVALID", "Each filter requires a field and operator");
    }
    String value = normalize(predicate.value());
    if (value == null || value.length() > 200) {
      throw invalid("FILTER_VALUE_INVALID", "Filter values must contain 1 to 200 characters");
    }
    if (TEXT_FIELDS.contains(predicate.field())) {
      requireOperator(predicate.operator(), SearchOperator.EQUALS, SearchOperator.CONTAINS);
      return new ValidatedSearchPredicate(
          predicate.field(), predicate.operator(), value, null, null, null);
    }
    if (NUMBER_FIELDS.contains(predicate.field())) {
      requireOperator(
          predicate.operator(),
          SearchOperator.EQUALS,
          SearchOperator.GREATER_THAN,
          SearchOperator.GREATER_THAN_OR_EQUAL,
          SearchOperator.LESS_THAN,
          SearchOperator.LESS_THAN_OR_EQUAL);
      long number = parseNonNegativeLong(value, "FILTER_NUMBER_INVALID");
      return new ValidatedSearchPredicate(
          predicate.field(), predicate.operator(), null, number, null, null);
    }
    if (DATE_FIELDS.contains(predicate.field())) {
      requireOperator(
          predicate.operator(), SearchOperator.ON, SearchOperator.BEFORE, SearchOperator.AFTER);
      try {
        return new ValidatedSearchPredicate(
            predicate.field(), predicate.operator(), null, null, LocalDate.parse(value), null);
      } catch (DateTimeParseException exception) {
        throw invalid("FILTER_DATE_INVALID", "Date filters must use ISO-8601 dates");
      }
    }
    if (COMPENSATION_FIELDS.contains(predicate.field())) {
      if (role != WorkspaceRole.WORKSPACE_ADMIN && role != WorkspaceRole.RECRUITER) {
        throw invalid("COMPENSATION_FORBIDDEN", "Compensation search is not permitted");
      }
      requireOperator(
          predicate.operator(),
          SearchOperator.EQUALS,
          SearchOperator.GREATER_THAN,
          SearchOperator.GREATER_THAN_OR_EQUAL,
          SearchOperator.LESS_THAN,
          SearchOperator.LESS_THAN_OR_EQUAL);
      String currency = normalizeCurrency(predicate.currency());
      long amountMinor = parseNonNegativeLong(value, "COMPENSATION_INVALID");
      return new ValidatedSearchPredicate(
          predicate.field(), predicate.operator(), null, amountMinor, null, currency);
    }
    throw invalid("FILTER_FIELD_FORBIDDEN", "Search field is not allowed");
  }

  private static SearchCursor decodeCursor(String encoded) {
    String normalized = normalize(encoded);
    if (normalized == null) {
      return null;
    }
    try {
      String decoded =
          new String(Base64.getUrlDecoder().decode(normalized), StandardCharsets.UTF_8);
      String[] parts = decoded.split("\\n", -1);
      if (parts.length != 2 || parts[0].isBlank()) {
        throw new IllegalArgumentException("invalid cursor");
      }
      return new SearchCursor(parts[0], UUID.fromString(parts[1]));
    } catch (IllegalArgumentException exception) {
      throw invalid("CURSOR_INVALID", "Search cursor is invalid");
    }
  }

  private static long parseNonNegativeLong(String value, String code) {
    try {
      long parsed = Long.parseLong(value);
      if (parsed < 0) {
        throw new NumberFormatException("negative");
      }
      return parsed;
    } catch (NumberFormatException exception) {
      throw invalid(code, "Numeric filter must be a non-negative whole number");
    }
  }

  private static String normalizeCurrency(String currency) {
    String normalized = normalize(currency);
    if (normalized == null || !normalized.matches("[A-Za-z]{3}")) {
      throw invalid("AMBIGUOUS_CURRENCY", "Compensation filters require an ISO currency");
    }
    return normalized.toUpperCase(Locale.ROOT);
  }

  private static void requireOperator(SearchOperator actual, SearchOperator... allowed) {
    for (SearchOperator candidate : allowed) {
      if (actual == candidate) {
        return;
      }
    }
    throw invalid("FILTER_OPERATOR_FORBIDDEN", "Operator is not allowed for this field");
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private static SearchValidationException invalid(String code, String message) {
    return new SearchValidationException(code, message);
  }
}
