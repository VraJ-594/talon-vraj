package com.talon.ats.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.talon.ats.identity.contract.WorkspaceRole;
import com.talon.ats.search.application.SearchDslValidator;
import com.talon.ats.search.application.SearchValidationException;
import com.talon.ats.search.domain.CandidateSearchCriteria;
import com.talon.ats.search.domain.SearchField;
import com.talon.ats.search.domain.SearchOperator;
import com.talon.ats.search.domain.SearchPredicate;
import com.talon.ats.search.domain.SearchSort;
import com.talon.ats.search.domain.SearchSortField;
import com.talon.ats.search.domain.SortDirection;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchDslValidatorTests {

  private final SearchDslValidator validator = new SearchDslValidator();

  @Test
  void validatesTypedTextNumberDateAndCompensationFilters() {
    var validated =
        validator.validate(
            criteria(
                List.of(
                    predicate(SearchField.LOCATION, SearchOperator.CONTAINS, " Bengaluru ", null),
                    predicate(
                        SearchField.EXPERIENCE_MONTHS,
                        SearchOperator.GREATER_THAN_OR_EQUAL,
                        "60",
                        null),
                    predicate(SearchField.APPLIED_AT, SearchOperator.AFTER, "2026-07-01", null),
                    predicate(
                        SearchField.EXPECTED_COMPENSATION,
                        SearchOperator.LESS_THAN_OR_EQUAL,
                        "400000000",
                        "inr"))),
            WorkspaceRole.RECRUITER);

    assertThat(validated.predicates()).hasSize(4);
    assertThat(validated.predicates().get(0).textValue()).isEqualTo("Bengaluru");
    assertThat(validated.predicates().get(1).numberValue()).isEqualTo(60L);
    assertThat(validated.predicates().get(2).dateValue()).hasToString("2026-07-01");
    assertThat(validated.predicates().get(3).numberValue()).isEqualTo(400_000_000L);
    assertThat(validated.predicates().get(3).currency()).isEqualTo("INR");
  }

  @Test
  void rejectsAFieldOperatorCombinationOutsideTheAllowlist() {
    assertThatThrownBy(
            () ->
                validator.validate(
                    criteria(
                        List.of(
                            predicate(
                                SearchField.LOCATION,
                                SearchOperator.GREATER_THAN_OR_EQUAL,
                                "Pune",
                                null))),
                    WorkspaceRole.RECRUITER))
        .isInstanceOf(SearchValidationException.class)
        .extracting(exception -> ((SearchValidationException) exception).code())
        .isEqualTo("FILTER_OPERATOR_FORBIDDEN");
  }

  @Test
  void deniesCompensationFiltersBeforePersistenceForRestrictedRoles() {
    assertThatThrownBy(
            () ->
                validator.validate(
                    criteria(
                        List.of(
                            predicate(
                                SearchField.CURRENT_COMPENSATION,
                                SearchOperator.LESS_THAN_OR_EQUAL,
                                "10000",
                                "USD"))),
                    WorkspaceRole.INTERVIEWER))
        .isInstanceOf(SearchValidationException.class)
        .extracting(exception -> ((SearchValidationException) exception).code())
        .isEqualTo("COMPENSATION_FORBIDDEN");
  }

  @Test
  void rejectsAmbiguousCompensationAndMalformedDates() {
    assertThatThrownBy(
            () ->
                validator.validate(
                    criteria(
                        List.of(
                            predicate(
                                SearchField.EXPECTED_COMPENSATION,
                                SearchOperator.LESS_THAN_OR_EQUAL,
                                "400000000",
                                null))),
                    WorkspaceRole.WORKSPACE_ADMIN))
        .isInstanceOf(SearchValidationException.class)
        .extracting(exception -> ((SearchValidationException) exception).code())
        .isEqualTo("AMBIGUOUS_CURRENCY");

    assertThatThrownBy(
            () ->
                validator.validate(
                    criteria(
                        List.of(
                            predicate(
                                SearchField.AVAILABLE_FROM,
                                SearchOperator.BEFORE,
                                "next friday",
                                null))),
                    WorkspaceRole.RECRUITER))
        .isInstanceOf(SearchValidationException.class)
        .extracting(exception -> ((SearchValidationException) exception).code())
        .isEqualTo("FILTER_DATE_INVALID");
  }

  @Test
  void appliesBoundedDefaultsAndRoundTripsAnOpaqueCursor() {
    var first = validator.validate(criteria(List.of()), WorkspaceRole.WORKSPACE_ADMIN);
    String cursor =
        validator.encodeCursor(
            new com.talon.ats.search.domain.SearchCursor(
                "2026-08-07", java.util.UUID.fromString("00000000-0000-0000-0000-000000000123")));
    var second =
        validator.validate(
            new CandidateSearchCriteria(
                "1", null, List.of(), SearchSort.newestFirst(), null, cursor),
            WorkspaceRole.WORKSPACE_ADMIN);

    assertThat(first.limit()).isEqualTo(25);
    assertThat(first.sort()).isEqualTo(SearchSort.newestFirst());
    assertThat(second.cursor().sortValue()).isEqualTo("2026-08-07");
    assertThat(second.cursor().applicationId()).hasToString("00000000-0000-0000-0000-000000000123");
  }

  private static CandidateSearchCriteria criteria(List<SearchPredicate> predicates) {
    return new CandidateSearchCriteria(
        "1",
        " java ",
        predicates,
        new SearchSort(SearchSortField.APPLIED_AT, SortDirection.DESC),
        25,
        null);
  }

  private static SearchPredicate predicate(
      SearchField field, SearchOperator operator, String value, String currency) {
    return new SearchPredicate(field, operator, value, currency);
  }
}
