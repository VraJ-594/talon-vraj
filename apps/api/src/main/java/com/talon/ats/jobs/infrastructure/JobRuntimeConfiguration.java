package com.talon.ats.jobs.infrastructure;

import com.talon.ats.jobs.application.JobRepository;
import com.talon.ats.jobs.application.JobService;
import com.talon.ats.jobs.infrastructure.persistence.JdbcJobRepository;
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
public class JobRuntimeConfiguration {

  @Bean
  JobRepository jobRepository(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
    return new JdbcJobRepository(jdbc, new TransactionTemplate(transactionManager));
  }

  @Bean
  JobService jobService(JobRepository repository) {
    return new JobService(repository, UUID::randomUUID, Clock.systemUTC());
  }
}
