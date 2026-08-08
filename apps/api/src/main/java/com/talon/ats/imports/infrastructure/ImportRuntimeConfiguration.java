package com.talon.ats.imports.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talon.ats.candidates.contract.CandidateImportAccess;
import com.talon.ats.files.application.ObjectStorage;
import com.talon.ats.files.application.ObjectStorageFactory;
import com.talon.ats.files.application.ResumeTransferService;
import com.talon.ats.imports.application.CsvApplicationParser;
import com.talon.ats.imports.application.ImportApplicationWorker;
import com.talon.ats.imports.application.ImportDraftRepository;
import com.talon.ats.imports.application.ImportDraftService;
import com.talon.ats.imports.application.ImportWorkDispatcher;
import com.talon.ats.imports.application.StrictTalonImportTemplate;
import com.talon.ats.imports.infrastructure.csv.CommonsCsvApplicationParser;
import com.talon.ats.imports.infrastructure.persistence.JdbcImportDraftRepository;
import com.talon.ats.jobs.contract.ImportTargetAccess;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "talon.security.enabled", havingValue = "true")
public class ImportRuntimeConfiguration {

  @Bean
  CsvApplicationParser csvApplicationParser() {
    return new CommonsCsvApplicationParser();
  }

  @Bean
  StrictTalonImportTemplate strictTalonImportTemplate() {
    return new StrictTalonImportTemplate();
  }

  @Bean
  ImportDraftRepository importDraftRepository(
      JdbcTemplate jdbc, PlatformTransactionManager transactionManager, ObjectMapper objectMapper) {
    return new JdbcImportDraftRepository(
        jdbc, new TransactionTemplate(transactionManager), objectMapper);
  }

  @Bean
  ObjectStorage importObjectStorage(
      ObjectStorageFactory storageFactory,
      @Value("${talon.files.provider:local}") String provider,
      @Value("${talon.files.local-root:${java.io.tmpdir}/talon-private-files}") String localRoot,
      @Value("${talon.files.s3.bucket:}") String s3Bucket,
      @Value("${talon.files.s3.region:}") String s3Region) {
    return switch (provider.trim().toLowerCase(Locale.ROOT)) {
      case "local" -> storageFactory.local(Path.of(localRoot));
      case "s3" ->
          storageFactory.s3(required(s3Bucket, "S3 bucket"), required(s3Region, "S3 region"));
      default ->
          throw new IllegalStateException(
              "configured private object storage provider is unavailable");
    };
  }

  @Bean
  ImportApplicationWorker importApplicationWorker(
      ImportDraftRepository repository,
      CandidateImportAccess candidates,
      ResumeTransferService resumes) {
    return new ImportApplicationWorker(repository, candidates, resumes, Clock.systemUTC());
  }

  @Bean(name = "importTaskExecutor")
  ThreadPoolTaskExecutor importTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("candidate-import-");
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(5);
    executor.setQueueCapacity(100);
    executor.initialize();
    return executor;
  }

  @Bean
  ImportWorkDispatcher importWorkDispatcher(
      @Qualifier("importTaskExecutor") TaskExecutor executor, ImportApplicationWorker worker) {
    return new LocalImportWorkDispatcher(executor, worker);
  }

  @Bean
  ImportDraftService importDraftService(
      CsvApplicationParser parser,
      StrictTalonImportTemplate template,
      ImportDraftRepository repository,
      ObjectStorage storage,
      ImportTargetAccess importTargets,
      ImportWorkDispatcher dispatcher) {
    return new ImportDraftService(
        parser,
        template,
        repository,
        storage,
        importTargets,
        UUID::randomUUID,
        Clock.systemUTC(),
        dispatcher);
  }

  private static String required(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(field + " is required for private S3 storage");
    }
    return value.trim();
  }
}
