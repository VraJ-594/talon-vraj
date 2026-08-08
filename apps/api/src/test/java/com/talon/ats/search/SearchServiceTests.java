package com.talon.ats.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.talon.ats.identity.contract.WorkspaceRole;
import com.talon.ats.search.application.CandidateSearchStore;
import com.talon.ats.search.application.CommandSearchItem;
import com.talon.ats.search.application.InterpretationRateLimiter;
import com.talon.ats.search.application.NaturalLanguageQuery;
import com.talon.ats.search.application.SearchActor;
import com.talon.ats.search.application.SearchDslValidator;
import com.talon.ats.search.application.SearchResultSlice;
import com.talon.ats.search.application.SearchService;
import com.talon.ats.search.domain.CandidateSearchCriteria;
import com.talon.ats.search.domain.SearchField;
import com.talon.ats.search.domain.SearchOperator;
import com.talon.ats.search.domain.SearchPredicate;
import com.talon.ats.search.domain.SearchSort;
import com.talon.ats.search.domain.ValidatedCandidateSearch;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SearchServiceTests {

  private static final UUID USER_ID = new UUID(0, 1);
  private static final UUID WORKSPACE_ID = new UUID(0, 2);

  @Test
  void interpretationReturnsEditableCriteriaWithoutExecutingSearch() {
    RecordingStore store = new RecordingStore();
    SearchService service =
        new SearchService(
            new SearchDslValidator(),
            store,
            query ->
                new CandidateSearchCriteria(
                    "1",
                    null,
                    List.of(
                        new SearchPredicate(
                            SearchField.EXPECTED_COMPENSATION,
                            SearchOperator.LESS_THAN_OR_EQUAL,
                            "400000000",
                            "INR")),
                    SearchSort.newestFirst(),
                    25,
                    null),
            new InterpretationRateLimiter(
                Clock.fixed(Instant.parse("2026-08-08T08:00:00Z"), ZoneOffset.UTC)));

    var interpreted =
        service.interpret(
            actor(), new NaturalLanguageQuery("Candidates below 40 LPA", "en-IN", "Asia/Kolkata"));

    assertThat(interpreted.criteria().predicates()).hasSize(1);
    assertThat(interpreted.chips().getFirst().value()).isEqualTo("40.00 LPA");
    assertThat(interpreted.warnings()).singleElement().asString().contains("annual INR");
    assertThat(store.searchCalls).isZero();
  }

  @Test
  void explicitQueryUsesTheSameValidatedCriteriaAndWorkspace() {
    RecordingStore store = new RecordingStore();
    SearchService service =
        new SearchService(
            new SearchDslValidator(),
            store,
            query -> {
              throw new AssertionError("interpreter must not be called");
            },
            new InterpretationRateLimiter(Clock.systemUTC()));

    service.query(
        actor(),
        new CandidateSearchCriteria("1", "Java", List.of(), SearchSort.newestFirst(), 25, null));

    assertThat(store.workspaceId).isEqualTo(WORKSPACE_ID);
    assertThat(store.search.text()).isEqualTo("Java");
    assertThat(store.searchCalls).isEqualTo(1);
  }

  private static SearchActor actor() {
    return new SearchActor(USER_ID, WORKSPACE_ID, WorkspaceRole.RECRUITER);
  }

  private static final class RecordingStore implements CandidateSearchStore {
    private int searchCalls;
    private UUID workspaceId;
    private ValidatedCandidateSearch search;

    @Override
    public SearchResultSlice search(UUID workspaceId, ValidatedCandidateSearch search) {
      searchCalls++;
      this.workspaceId = workspaceId;
      this.search = search;
      return new SearchResultSlice(List.of(), null);
    }

    @Override
    public List<CommandSearchItem> command(UUID workspaceId, String query, int limit) {
      return List.of();
    }
  }
}
