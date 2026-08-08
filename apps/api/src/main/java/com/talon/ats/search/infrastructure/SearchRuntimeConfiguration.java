package com.talon.ats.search.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talon.ats.search.application.CandidateSearchStore;
import com.talon.ats.search.application.InterpretationRateLimiter;
import com.talon.ats.search.application.NaturalLanguageSearchInterpreter;
import com.talon.ats.search.application.SearchDslValidator;
import com.talon.ats.search.application.SearchService;
import com.talon.ats.search.infrastructure.groq.DisabledNaturalLanguageSearchInterpreter;
import com.talon.ats.search.infrastructure.groq.GroqNaturalLanguageSearchInterpreter;
import com.talon.ats.search.infrastructure.postgres.PostgresCandidateSearchStore;
import java.net.URI;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "talon.security.enabled", havingValue = "true")
public class SearchRuntimeConfiguration {

  @Bean
  SearchDslValidator searchDslValidator() {
    return new SearchDslValidator();
  }

  @Bean
  CandidateSearchStore candidateSearchStore(
      JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
    return new PostgresCandidateSearchStore(
        new NamedParameterJdbcTemplate(jdbc), new TransactionTemplate(transactionManager));
  }

  @Bean
  NaturalLanguageSearchInterpreter naturalLanguageSearchInterpreter(
      ObjectMapper objectMapper,
      @Value("${talon.search.ai.enabled:false}") boolean enabled,
      @Value("${talon.search.ai.api-key:}") String apiKey,
      @Value("${talon.search.ai.model:openai/gpt-oss-20b}") String model,
      @Value("${talon.search.ai.endpoint:https://api.groq.com/openai/v1/chat/completions}")
          String endpoint) {
    if (!enabled) {
      return new DisabledNaturalLanguageSearchInterpreter();
    }
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException("GROQ_API_KEY is required when AI search is enabled");
    }
    return new GroqNaturalLanguageSearchInterpreter(
        objectMapper, URI.create(endpoint), apiKey.trim(), model.trim());
  }

  @Bean
  InterpretationRateLimiter interpretationRateLimiter() {
    return new InterpretationRateLimiter(Clock.systemUTC());
  }

  @Bean
  SearchService searchService(
      SearchDslValidator validator,
      CandidateSearchStore store,
      NaturalLanguageSearchInterpreter interpreter,
      InterpretationRateLimiter rateLimiter) {
    return new SearchService(validator, store, interpreter, rateLimiter);
  }

  @Bean
  @ConditionalOnProperty(name = "talon.search.demo-data.enabled", havingValue = "true")
  SearchDemoDataProvisioner searchDemoDataProvisioner(
      JdbcTemplate jdbc,
      PlatformTransactionManager transactionManager,
      @Value("${talon.search.demo-data.workspace-slug:talon-demo}") String workspaceSlug) {
    return new SearchDemoDataProvisioner(
        jdbc, new TransactionTemplate(transactionManager), workspaceSlug.trim());
  }
}
