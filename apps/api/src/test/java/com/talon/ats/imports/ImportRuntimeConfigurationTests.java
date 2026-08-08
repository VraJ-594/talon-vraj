package com.talon.ats.imports;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talon.ats.candidates.contract.CandidateImportAccess;
import com.talon.ats.files.application.ExternalFileMetadata;
import com.talon.ats.files.application.ObjectStorage;
import com.talon.ats.files.application.ObjectStorageFactory;
import com.talon.ats.files.application.ResumeTransferService;
import com.talon.ats.files.infrastructure.storage.LocalObjectStorage;
import com.talon.ats.imports.application.ImportDraftRepository;
import com.talon.ats.imports.application.ImportDraftService;
import com.talon.ats.imports.infrastructure.ImportRuntimeConfiguration;
import com.talon.ats.jobs.contract.ImportTargetAccess;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;

class ImportRuntimeConfigurationTests {

  private static final DataSource DATA_SOURCE =
      new DriverManagerDataSource("jdbc:postgresql://localhost:1/not-used", "none", "none");

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(ImportRuntimeConfiguration.class)
          .withBean(JdbcTemplate.class, () -> new JdbcTemplate(DATA_SOURCE))
          .withBean(
              PlatformTransactionManager.class, () -> new DataSourceTransactionManager(DATA_SOURCE))
          .withBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules())
          .withBean(ObjectStorageFactory.class, () -> LocalObjectStorage::new)
          .withBean(
              ResumeTransferService.class,
              () ->
                  new ResumeTransferService(
                      (source, sink) -> new ExternalFileMetadata(1, "application/pdf"),
                      new LocalObjectStorage(
                          Path.of("target", "runtime-resume-files").toAbsolutePath())))
          .withBean(
              CandidateImportAccess.class,
              () ->
                  (actor, application) ->
                      new CandidateImportAccess.Result(
                          java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), true, true))
          .withBean(ImportTargetAccess.class, () -> (workspaceId, jobId) -> true);

  @Test
  void selectsPrivateS3StorageOnlyWhenBucketAndRegionAreConfigured() {
    ObjectStorage expected = new LocalObjectStorage(Path.of("target", "s3-selection-sentinel"));
    ObjectStorageFactory factory =
        new ObjectStorageFactory() {
          @Override
          public ObjectStorage local(Path root) {
            throw new AssertionError("local storage must not be selected");
          }

          @Override
          public ObjectStorage s3(String bucket, String region) {
            assertThat(bucket).isEqualTo("talon-resumes-demo-vraj");
            assertThat(region).isEqualTo("ap-south-1");
            return expected;
          }
        };

    contextRunner
        .withBean(
            "s3StorageFactory",
            ObjectStorageFactory.class,
            () -> factory,
            definition -> definition.setPrimary(true))
        .withPropertyValues(
            "talon.security.enabled=true",
            "talon.files.provider=s3",
            "talon.files.s3.bucket=talon-resumes-demo-vraj",
            "talon.files.s3.region=ap-south-1")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(ObjectStorage.class)).isSameAs(expected);
            });
  }

  @Test
  void enablesTheCompleteLocalImportRuntimeOnlyWithSecurity() {
    String root = Path.of("target", "runtime-private-files").toAbsolutePath().toString();
    contextRunner
        .withPropertyValues(
            "talon.security.enabled=true",
            "talon.files.provider=local",
            "talon.files.local-root=" + root)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(ImportDraftService.class);
              assertThat(context).hasSingleBean(ImportDraftRepository.class);
              assertThat(context).hasSingleBean(ObjectStorage.class);
              assertThat(context.getBean(ObjectStorage.class))
                  .isInstanceOf(LocalObjectStorage.class);
            });
  }

  @Test
  void remainsDisabledWhenApplicationSecurityIsDisabled() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).doesNotHaveBean(ImportDraftService.class);
          assertThat(context).doesNotHaveBean(ObjectStorage.class);
        });
  }

  @Test
  void failsClosedForAnUnsupportedStorageProvider() {
    contextRunner
        .withPropertyValues(
            "talon.security.enabled=true",
            "talon.files.provider=azure",
            "talon.files.local-root=target/runtime-private-files")
        .run(context -> assertThat(context).hasFailed());
  }
}
