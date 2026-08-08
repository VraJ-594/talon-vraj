package com.talon.ats.imports;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talon.ats.files.application.PrivateObjectKey;
import com.talon.ats.imports.application.CsvParseResult;
import com.talon.ats.imports.application.CsvPreviewIssue;
import com.talon.ats.imports.application.ImportDraft;
import com.talon.ats.imports.application.ImportDraftRepository;
import com.talon.ats.imports.application.ImportPreviewSnapshot;
import com.talon.ats.imports.application.NormalizedApplicationRow;
import com.talon.ats.imports.application.ParsedApplicationRow;
import com.talon.ats.imports.domain.CanonicalField;
import com.talon.ats.imports.domain.ColumnMapping;
import com.talon.ats.imports.domain.ImportStatus;
import com.talon.ats.imports.infrastructure.persistence.JdbcImportDraftRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class SupabaseImportDraftPersistenceIT {

  private static final UUID WORKSPACE_ID = UUID.randomUUID();
  private static final UUID OTHER_WORKSPACE_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID MEMBERSHIP_ID = UUID.randomUUID();
  private static final UUID JOB_ID = UUID.randomUUID();
  private static final UUID IMPORT_ID = UUID.randomUUID();
  private static final Instant CREATED_AT = Instant.parse("2026-08-08T05:30:00Z");
  private static JdbcTemplate jdbc;
  private static ImportDraftRepository repository;

  @BeforeAll
  static void migrateAndSeed() {
    String url = required("DATABASE_URL");
    String username = required("DATABASE_USERNAME");
    String password = required("DATABASE_PASSWORD");
    Flyway.configure()
        .dataSource(url, username, password)
        .locations("classpath:db/migration")
        .load()
        .migrate();

    DriverManagerDataSource dataSource = new DriverManagerDataSource(url, username, password);
    jdbc = new JdbcTemplate(dataSource);
    repository =
        new JdbcImportDraftRepository(
            jdbc,
            new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
            new ObjectMapper().findAndRegisterModules());
    seed();
  }

  @AfterAll
  static void cleanSyntheticTenant() {
    if (jdbc == null) {
      return;
    }
    jdbc.update("DELETE FROM candidate_import_row WHERE workspace_id = ?", WORKSPACE_ID);
    jdbc.update("DELETE FROM candidate_import WHERE workspace_id = ?", WORKSPACE_ID);
    jdbc.update("DELETE FROM job WHERE workspace_id = ?", WORKSPACE_ID);
    jdbc.update("DELETE FROM workspace_membership WHERE workspace_id = ?", WORKSPACE_ID);
    jdbc.update("DELETE FROM app_user WHERE id = ?", USER_ID);
    jdbc.update("DELETE FROM workspace WHERE id = ?", WORKSPACE_ID);
  }

  @BeforeEach
  void clearPriorImportState() {
    jdbc.update("DELETE FROM candidate_import_row WHERE workspace_id = ?", WORKSPACE_ID);
    jdbc.update("DELETE FROM candidate_import WHERE workspace_id = ?", WORKSPACE_ID);
  }

  @Test
  void createsReadsAndTenantScopesADurableDraft() {
    ImportDraft draft = draft();

    repository.create(draft);

    assertThat(repository.find(WORKSPACE_ID, IMPORT_ID))
        .get()
        .satisfies(
            stored -> {
              assertThat(stored.id()).isEqualTo(IMPORT_ID);
              assertThat(stored.jobId()).isEqualTo(JOB_ID);
              assertThat(stored.createdBy()).isEqualTo(USER_ID);
              assertThat(stored.fileName()).isEqualTo("applications.csv");
              assertThat(stored.sourceObjectKey())
                  .isEqualTo(PrivateObjectKey.importSource(WORKSPACE_ID, IMPORT_ID));
              assertThat(stored.sourceColumns())
                  .containsExactly("first_name", "last_name", "email", "resume_drive_url");
              assertThat(stored.suggestedMapping()).containsAllEntriesOf(mapping().assignments());
              assertThat(stored.status()).isEqualTo(ImportStatus.UPLOADED);
            });
    assertThat(repository.find(OTHER_WORKSPACE_ID, IMPORT_ID)).isEmpty();
  }

  @Test
  void atomicallyReplacesPreviewRowsAndRestoresSafeIssues() {
    repository.create(draft());

    ImportPreviewSnapshot first =
        repository.replacePreview(
            WORKSPACE_ID, IMPORT_ID, mapping(), firstResult(), CREATED_AT.plusSeconds(60));
    ImportPreviewSnapshot replacement =
        repository.replacePreview(
            WORKSPACE_ID, IMPORT_ID, mapping(), replacementResult(), CREATED_AT.plusSeconds(120));

    assertThat(first.validCount()).isEqualTo(1);
    assertThat(first.invalidCount()).isEqualTo(1);
    assertThat(first.duplicateCount()).isEqualTo(1);
    assertThat(replacement.validCount()).isEqualTo(2);
    assertThat(replacement.invalidCount()).isEqualTo(1);
    assertThat(replacement.duplicateCount()).isZero();
    assertThat(repository.findPreview(WORKSPACE_ID, IMPORT_ID)).contains(replacement);
    assertThat(repository.findPreview(OTHER_WORKSPACE_ID, IMPORT_ID)).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM candidate_import_row WHERE workspace_id = ? AND import_id = ?",
                Integer.class,
                WORKSPACE_ID,
                IMPORT_ID))
        .isEqualTo(3);
  }

  private static ImportDraft draft() {
    return new ImportDraft(
        IMPORT_ID,
        WORKSPACE_ID,
        JOB_ID,
        USER_ID,
        "applications.csv",
        PrivateObjectKey.importSource(WORKSPACE_ID, IMPORT_ID),
        3,
        List.of("first_name", "last_name", "email", "resume_drive_url"),
        mapping().assignments(),
        ImportStatus.UPLOADED,
        0,
        CREATED_AT,
        CREATED_AT);
  }

  private static CsvParseResult firstResult() {
    return new CsvParseResult(
        1,
        1,
        1,
        List.of(validRow(2, "first@example.com")),
        List.of(
            new CsvPreviewIssue(3, "INVALID", "INVALID_EMAIL", "email must be valid"),
            new CsvPreviewIssue(
                4, "DUPLICATE", "DUPLICATE_IN_FILE", "candidate email is duplicated in this CSV")));
  }

  private static CsvParseResult replacementResult() {
    return new CsvParseResult(
        2,
        1,
        0,
        List.of(validRow(2, "first@example.com"), validRow(3, "second@example.com")),
        List.of(new CsvPreviewIssue(4, "INVALID", "INVALID_EMAIL", "email must be valid")));
  }

  private static ParsedApplicationRow validRow(int rowNumber, String email) {
    return new ParsedApplicationRow(
        new NormalizedApplicationRow(
            rowNumber,
            "Synthetic",
            "Candidate",
            email,
            "https://drive.google.com/file/d/example/view",
            null,
            null,
            null,
            null,
            null),
        Map.of());
  }

  private static ColumnMapping mapping() {
    return ColumnMapping.from(
        Map.of(
            "first_name",
            CanonicalField.FIRST_NAME,
            "last_name",
            CanonicalField.LAST_NAME,
            "email",
            CanonicalField.EMAIL,
            "resume_drive_url",
            CanonicalField.RESUME_DRIVE_URL));
  }

  private static void seed() {
    jdbc.update(
        "INSERT INTO workspace(id,name,slug,default_timezone,status) VALUES (?,?,?,?,?)",
        WORKSPACE_ID,
        "Synthetic Import Workspace",
        "synthetic-import-" + WORKSPACE_ID,
        "UTC",
        "ACTIVE");
    jdbc.update(
        "INSERT INTO app_user(id,email,normalized_email,display_name,password_hash,status,default_workspace_id) VALUES (?,?,?,?,?,?,?)",
        USER_ID,
        USER_ID + "@example.com",
        USER_ID + "@example.com",
        "Synthetic Recruiter",
        "$2a$12$WjJ0PmyFD6h7xjNQxJV13u98Ot4GY9NaAcNn0me04X6NxoJ/Oc9cW",
        "ACTIVE",
        WORKSPACE_ID);
    jdbc.update(
        "INSERT INTO workspace_membership(id,workspace_id,user_id,role,status) VALUES (?,?,?,?,?)",
        MEMBERSHIP_ID,
        WORKSPACE_ID,
        USER_ID,
        "RECRUITER",
        "ACTIVE");
    jdbc.update(
        "INSERT INTO job(id,workspace_id,title,department_name,location,status) VALUES (?,?,?,?,?,?)",
        JOB_ID,
        WORKSPACE_ID,
        "Synthetic Import Engineer",
        "Engineering",
        "Remote",
        "ACTIVE");
  }

  private static String required(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank() || value.contains("<")) {
      throw new IllegalStateException(name + " must be supplied by the ignored .env.supabase file");
    }
    return value;
  }
}
