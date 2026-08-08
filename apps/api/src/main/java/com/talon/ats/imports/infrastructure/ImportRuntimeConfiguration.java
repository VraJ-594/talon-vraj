package com.talon.ats.imports.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talon.ats.files.application.ObjectStorage;
import com.talon.ats.files.application.ObjectStorageFactory;
import com.talon.ats.imports.application.CsvApplicationParser;
import com.talon.ats.imports.application.ImportDraftRepository;
import com.talon.ats.imports.application.ImportDraftService;
import com.talon.ats.imports.application.StrictTalonImportTemplate;
import com.talon.ats.imports.infrastructure.csv.CommonsCsvApplicationParser;
import com.talon.ats.imports.infrastructure.persistence.JdbcImportDraftRepository;
import com.talon.ats.jobs.contract.ImportTargetAccess;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
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
      @Value("${talon.files.local-root:${java.io.tmpdir}/talon-private-files}") String localRoot) {
    if (!"local".equals(provider.trim().toLowerCase(Locale.ROOT))) {
      throw new IllegalStateException("configured private object storage provider is unavailable");
    }
    return storageFactory.local(Path.of(localRoot));
  }

  @Bean
  ImportDraftService importDraftService(
      CsvApplicationParser parser,
      StrictTalonImportTemplate template,
      ImportDraftRepository repository,
      ObjectStorage storage,
      ImportTargetAccess importTargets) {
    return new ImportDraftService(
        parser, template, repository, storage, importTargets, UUID::randomUUID, Clock.systemUTC());
  }
}
