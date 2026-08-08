package com.talon.ats.search.api;

import com.talon.ats.identity.contract.WorkspaceRole;
import com.talon.ats.search.application.CandidateSearchPage;
import com.talon.ats.search.application.CommandSearchItem;
import com.talon.ats.search.application.InterpretedCandidateSearch;
import com.talon.ats.search.application.NaturalLanguageQuery;
import com.talon.ats.search.application.SearchActor;
import com.talon.ats.search.application.SearchService;
import com.talon.ats.search.domain.CandidateSearchCriteria;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "talon.security.enabled", havingValue = "true")
public class SearchController {

  private final SearchService searchService;

  public SearchController(SearchService searchService) {
    this.searchService = searchService;
  }

  @GetMapping("/api/v1/search/command")
  List<CommandSearchItem> command(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam String q,
      @RequestParam(defaultValue = "8") int limit) {
    return searchService.command(actor(jwt), q, limit);
  }

  @PostMapping("/api/v1/candidate-search/interpret")
  InterpretedCandidateSearch interpret(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody InterpretRequest request) {
    return searchService.interpret(
        actor(jwt),
        new NaturalLanguageQuery(request.query(), request.locale(), request.timezone()));
  }

  @PostMapping("/api/v1/candidate-search/query")
  CandidateSearchPage query(
      @AuthenticationPrincipal Jwt jwt, @RequestBody CandidateSearchCriteria criteria) {
    return searchService.query(actor(jwt), criteria);
  }

  private static SearchActor actor(Jwt jwt) {
    return new SearchActor(
        UUID.fromString(jwt.getSubject()),
        UUID.fromString(jwt.getClaimAsString("workspace_id")),
        WorkspaceRole.valueOf(jwt.getClaimAsString("role")));
  }

  record InterpretRequest(
      @NotBlank @Size(max = 500) String query,
      @Size(max = 35) String locale,
      @Size(max = 100) String timezone) {}
}
