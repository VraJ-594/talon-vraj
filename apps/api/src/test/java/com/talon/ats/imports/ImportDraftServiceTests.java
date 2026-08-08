package com.talon.ats.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.talon.ats.files.application.ObjectStorage;
import com.talon.ats.files.application.PrivateObjectKey;
import com.talon.ats.files.application.StoredObject;
import com.talon.ats.identity.contract.WorkspaceRole;
import com.talon.ats.imports.application.CsvApplicationParser;
import com.talon.ats.imports.application.CsvInspection;
import com.talon.ats.imports.application.CsvParseException;
import com.talon.ats.imports.application.CsvParseResult;
import com.talon.ats.imports.application.ImportDraft;
import com.talon.ats.imports.application.ImportDraftRepository;
import com.talon.ats.imports.application.ImportDraftService;
import com.talon.ats.imports.application.ImportPreviewSnapshot;
import com.talon.ats.imports.application.ImportProblem;
import com.talon.ats.imports.application.StrictTalonImportTemplate;
import com.talon.ats.imports.domain.CanonicalField;
import com.talon.ats.imports.domain.ColumnMapping;
import com.talon.ats.imports.domain.ImportStatus;
import com.talon.ats.jobs.contract.ImportTargetAccess;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImportDraftServiceTests {

  private static final UUID USER_ID = new UUID(0, 1);
  private static final UUID WORKSPACE_ID = new UUID(0, 2);
  private static final UUID OTHER_WORKSPACE_ID = new UUID(0, 3);
  private static final UUID JOB_ID = new UUID(0, 4);
  private static final UUID IMPORT_ID = new UUID(0, 5);
  private static final Instant NOW = Instant.parse("2026-08-08T06:00:00Z");
  private static final List<String> REQUIRED_COLUMNS =
      List.of("first_name", "last_name", "email", "resume_drive_url");

  @Test
  void inspectsAndAuthorizesBeforeWritingAnOpaquePrivateObjectAndDraft() {
    Fixture fixture = new Fixture();

    ImportDraft draft =
        fixture.service().upload(actor(), JOB_ID, "C:\\fakepath\\applications.csv", upload());

    assertThat(fixture.events).containsExactly("inspect", "find-job", "put", "create-draft");
    assertThat(draft.id()).isEqualTo(IMPORT_ID);
    assertThat(draft.workspaceId()).isEqualTo(WORKSPACE_ID);
    assertThat(draft.createdBy()).isEqualTo(USER_ID);
    assertThat(draft.fileName()).isEqualTo("applications.csv");
    assertThat(draft.sourceObjectKey())
        .isEqualTo(PrivateObjectKey.importSource(WORKSPACE_ID, IMPORT_ID));
    assertThat(draft.suggestedMapping()).containsExactlyEntriesOf(strictMapping());
    assertThat(draft.status()).isEqualTo(ImportStatus.UPLOADED);
    assertThat(fixture.storage.putMaximumBytes).isEqualTo(10L * 1024 * 1024);
  }

  @Test
  void rejectsAnUnsupportedHeaderBeforeJobStorageOrPersistenceAccess() {
    Fixture fixture = new Fixture();
    fixture.parser.inspection =
        new CsvInspection(
            List.of("first_name", "last_name", "email", "resume_drive_url", "favorite_color"),
            1,
            Map.of());

    assertThatThrownBy(
            () -> fixture.service().upload(actor(), JOB_ID, "applications.csv", upload()))
        .isInstanceOf(CsvParseException.class)
        .extracting(error -> ((CsvParseException) error).code())
        .isEqualTo("UNSUPPORTED_SOURCE_COLUMN");
    assertThat(fixture.events).containsExactly("inspect");
  }

  @Test
  void rejectsNonRecruitingRolesBeforeReadingTheUpload() {
    Fixture fixture = new Fixture();

    assertThatThrownBy(
            () ->
                fixture
                    .service()
                    .upload(
                        new ImportDraftService.Actor(
                            USER_ID, WORKSPACE_ID, WorkspaceRole.INTERVIEWER),
                        JOB_ID,
                        "applications.csv",
                        upload()))
        .isInstanceOf(SecurityException.class);
    assertThat(fixture.events).isEmpty();
  }

  @Test
  void rejectsAClosedOrCrossWorkspaceJobWithoutWritingAnything() {
    Fixture fixture = new Fixture();
    fixture.importTargets.importable = false;

    assertProblem(
        "JOB_NOT_IMPORTABLE",
        () -> fixture.service().upload(actor(), JOB_ID, "applications.csv", upload()));
    assertThat(fixture.events).containsExactly("inspect", "find-job");

    fixture.events.clear();
    assertProblem(
        "JOB_NOT_IMPORTABLE",
        () -> fixture.service().upload(actor(), JOB_ID, "applications.csv", upload()));
    assertThat(fixture.events).containsExactly("inspect", "find-job");
  }

  @Test
  void deletesThePrivateObjectWhenDraftPersistenceFails() {
    Fixture fixture = new Fixture();
    fixture.repository.failCreate = true;

    assertProblem(
        "IMPORT_STORAGE_FAILED",
        () -> fixture.service().upload(actor(), JOB_ID, "applications.csv", upload()));

    assertThat(fixture.events)
        .containsExactly("inspect", "find-job", "put", "create-draft", "delete");
    assertThat(fixture.storage.deleted)
        .isEqualTo(PrivateObjectKey.importSource(WORKSPACE_ID, IMPORT_ID));
  }

  @Test
  void validatesTheExactMappingFromTheTenantDraftAndAtomicallyReturnsPreview() {
    Fixture fixture = new Fixture();
    ImportDraft draft = fixture.draft(WORKSPACE_ID);
    fixture.repository.draft = Optional.of(draft);
    fixture.repository.preview =
        new ImportPreviewSnapshot(IMPORT_ID, 1, 0, 0, List.of(), ImportStatus.PREVIEW_READY);

    ImportPreviewSnapshot preview =
        fixture.service().validate(actor(), IMPORT_ID, strictMapping(), false);

    assertThat(preview).isEqualTo(fixture.repository.preview);
    assertThat(fixture.events).containsExactly("find-draft", "open", "parse", "replace-preview");
    assertThat(fixture.parser.parsedMapping.assignments())
        .containsExactlyEntriesOf(strictMapping());
    assertThat(fixture.parser.retainUnmapped).isFalse();
  }

  @Test
  void hidesMissingOrCrossWorkspaceDraftsWithoutOpeningStorage() {
    Fixture fixture = new Fixture();
    fixture.repository.draft = Optional.empty();

    assertProblem(
        "IMPORT_NOT_FOUND",
        () -> fixture.service().validate(actor(), IMPORT_ID, strictMapping(), false));
    assertThat(fixture.events).containsExactly("find-draft");

    fixture.events.clear();
    fixture.repository.draft = Optional.of(fixture.draft(OTHER_WORKSPACE_ID));
    assertProblem(
        "IMPORT_NOT_FOUND",
        () -> fixture.service().validate(actor(), IMPORT_ID, strictMapping(), false));
    assertThat(fixture.events).containsExactly("find-draft");
  }

  @Test
  void restoresOnlyATenantOwnedDurablePreview() {
    Fixture fixture = new Fixture();
    fixture.repository.preview =
        new ImportPreviewSnapshot(IMPORT_ID, 1, 0, 0, List.of(), ImportStatus.PREVIEW_READY);

    assertThat(fixture.service().preview(actor(), IMPORT_ID)).isEqualTo(fixture.repository.preview);

    fixture.repository.preview = null;
    assertProblem("IMPORT_NOT_FOUND", () -> fixture.service().preview(actor(), IMPORT_ID));
  }

  private static void assertProblem(String code, Runnable action) {
    assertThatThrownBy(action::run)
        .isInstanceOf(ImportProblem.class)
        .extracting(error -> ((ImportProblem) error).code())
        .isEqualTo(code);
  }

  private static ImportDraftService.Actor actor() {
    return new ImportDraftService.Actor(USER_ID, WORKSPACE_ID, WorkspaceRole.RECRUITER);
  }

  private static ImportDraftService.ReopenableUpload upload() {
    return () ->
        new ByteArrayInputStream("synthetic CSV".getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private static Map<String, CanonicalField> strictMapping() {
    Map<String, CanonicalField> mapping = new LinkedHashMap<>();
    mapping.put("first_name", CanonicalField.FIRST_NAME);
    mapping.put("last_name", CanonicalField.LAST_NAME);
    mapping.put("email", CanonicalField.EMAIL);
    mapping.put("resume_drive_url", CanonicalField.RESUME_DRIVE_URL);
    return Collections.unmodifiableMap(mapping);
  }

  private static final class Fixture {
    private final List<String> events = new ArrayList<>();
    private final RecordingParser parser = new RecordingParser(events);
    private final RecordingImportTargets importTargets = new RecordingImportTargets(events);
    private final RecordingStorage storage = new RecordingStorage(events);
    private final RecordingRepository repository = new RecordingRepository(events);

    private ImportDraftService service() {
      return new ImportDraftService(
          parser,
          new StrictTalonImportTemplate(),
          repository,
          storage,
          importTargets,
          () -> IMPORT_ID,
          Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private ImportDraft draft(UUID workspaceId) {
      return new ImportDraft(
          IMPORT_ID,
          workspaceId,
          JOB_ID,
          USER_ID,
          "applications.csv",
          PrivateObjectKey.importSource(workspaceId, IMPORT_ID),
          1,
          REQUIRED_COLUMNS,
          strictMapping(),
          ImportStatus.UPLOADED,
          0,
          NOW,
          NOW);
    }
  }

  private static final class RecordingParser implements CsvApplicationParser {
    private final List<String> events;
    private CsvInspection inspection = new CsvInspection(REQUIRED_COLUMNS, 1, Map.of());
    private CsvParseResult parseResult = new CsvParseResult(0, 0, 0, List.of(), List.of());
    private ColumnMapping parsedMapping;
    private boolean retainUnmapped;

    private RecordingParser(List<String> events) {
      this.events = events;
    }

    @Override
    public CsvInspection inspect(InputStream input) {
      events.add("inspect");
      return inspection;
    }

    @Override
    public CsvParseResult parse(InputStream input, ColumnMapping mapping, boolean retainUnmapped) {
      events.add("parse");
      parsedMapping = mapping;
      this.retainUnmapped = retainUnmapped;
      return parseResult;
    }
  }

  private static final class RecordingImportTargets implements ImportTargetAccess {
    private final List<String> events;
    private boolean importable = true;

    private RecordingImportTargets(List<String> events) {
      this.events = events;
    }

    @Override
    public boolean isImportable(UUID workspaceId, UUID jobId) {
      events.add("find-job");
      return importable;
    }
  }

  private static final class RecordingStorage implements ObjectStorage {
    private final List<String> events;
    private long putMaximumBytes;
    private PrivateObjectKey deleted;

    private RecordingStorage(List<String> events) {
      this.events = events;
    }

    @Override
    public StoredObject put(PrivateObjectKey key, InputStream input, long maximumBytes) {
      events.add("put");
      putMaximumBytes = maximumBytes;
      return new StoredObject(key, 13, "a".repeat(64));
    }

    @Override
    public InputStream open(PrivateObjectKey key) {
      events.add("open");
      return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public boolean exists(PrivateObjectKey key) {
      return true;
    }

    @Override
    public void delete(PrivateObjectKey key) {
      events.add("delete");
      deleted = key;
    }

    @Override
    public void promote(PrivateObjectKey quarantine, PrivateObjectKey clean) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class RecordingRepository implements ImportDraftRepository {
    private final List<String> events;
    private Optional<ImportDraft> draft = Optional.empty();
    private ImportPreviewSnapshot preview;
    private boolean failCreate;

    private RecordingRepository(List<String> events) {
      this.events = events;
    }

    @Override
    public ImportDraft create(ImportDraft draft) {
      events.add("create-draft");
      if (failCreate) {
        throw new IllegalStateException("synthetic database failure");
      }
      this.draft = Optional.of(draft);
      return draft;
    }

    @Override
    public Optional<ImportDraft> find(UUID workspaceId, UUID importId) {
      events.add("find-draft");
      return draft.filter(candidate -> candidate.workspaceId().equals(workspaceId));
    }

    @Override
    public ImportPreviewSnapshot replacePreview(
        UUID workspaceId,
        UUID importId,
        ColumnMapping mapping,
        CsvParseResult result,
        Instant changedAt) {
      events.add("replace-preview");
      return preview;
    }

    @Override
    public Optional<ImportPreviewSnapshot> findPreview(UUID workspaceId, UUID importId) {
      return Optional.ofNullable(preview);
    }
  }
}
