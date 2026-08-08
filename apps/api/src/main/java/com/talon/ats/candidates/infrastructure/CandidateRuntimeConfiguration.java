package com.talon.ats.candidates.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talon.ats.candidates.application.CandidateApplicationService;
import com.talon.ats.candidates.application.CandidateApplicationStore;
import com.talon.ats.candidates.contract.CandidateImportAccess;
import com.talon.ats.candidates.infrastructure.persistence.JdbcCandidateApplicationStore;
import com.talon.ats.candidates.infrastructure.persistence.JdbcCandidateImportAccess;
import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "talon.security.enabled", havingValue = "true")
public class CandidateRuntimeConfiguration {

  @Bean
  CandidateApplicationStore candidateApplicationStore(
      JdbcTemplate jdbc, PlatformTransactionManager transactionManager, ObjectMapper objectMapper) {
    return new JdbcCandidateApplicationStore(
        jdbc, new TransactionTemplate(transactionManager), objectMapper);
  }

  @Bean
  CandidateApplicationService candidateApplicationService(CandidateApplicationStore store) {
    return new CandidateApplicationService(store, UUID::randomUUID, Clock.systemUTC());
  }

  @Bean
  CandidateImportAccess candidateImportAccess(
      CandidateApplicationService service,
      JdbcTemplate jdbc,
      PlatformTransactionManager transactionManager) {
    return new JdbcCandidateImportAccess(
        service, jdbc, new TransactionTemplate(transactionManager));
  }
}
