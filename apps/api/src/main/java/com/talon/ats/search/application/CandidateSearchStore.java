package com.talon.ats.search.application;

import com.talon.ats.search.domain.ValidatedCandidateSearch;
import java.util.List;
import java.util.UUID;

public interface CandidateSearchStore {

  SearchResultSlice search(UUID workspaceId, ValidatedCandidateSearch search);

  List<CommandSearchItem> command(UUID workspaceId, String query, int limit);
}
