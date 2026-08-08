package com.talon.ats.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.talon.ats.files.application.ObjectSizeLimitException;
import com.talon.ats.files.application.PrivateObjectKey;
import com.talon.ats.files.application.StoredObject;
import com.talon.ats.files.infrastructure.storage.LocalObjectStorage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PrivateObjectStorageTests {

  private static final UUID WORKSPACE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID FILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
  private static final UUID VERSION_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");

  private Path root;

  @BeforeEach
  void createWorkspaceLocalTemporaryRoot() throws IOException {
    Path testRoot = Path.of("target", "private-storage-tests").toAbsolutePath().normalize();
    Files.createDirectories(testRoot);
    root = Files.createTempDirectory(testRoot, "case-");
  }

  @AfterEach
  void removeWorkspaceLocalTemporaryRoot() throws IOException {
    try (var paths = Files.walk(root)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(path);
      }
    }
  }

  @Test
  void createsOnlyOpaqueCategorySpecificKeys() {
    assertThat(PrivateObjectKey.quarantineResume(WORKSPACE_ID, FILE_ID, VERSION_ID).value())
        .isEqualTo(
            "quarantine/10000000-0000-0000-0000-000000000001/resumes/"
                + "20000000-0000-0000-0000-000000000002/"
                + "30000000-0000-0000-0000-000000000003.pdf");
    assertThat(PrivateObjectKey.cleanResume(WORKSPACE_ID, FILE_ID, VERSION_ID).value())
        .startsWith("clean/")
        .endsWith(".pdf");
    assertThat(PrivateObjectKey.importSource(WORKSPACE_ID, FILE_ID).value())
        .isEqualTo(
            "imports/10000000-0000-0000-0000-000000000001/"
                + "20000000-0000-0000-0000-000000000002/source.csv");
    assertThat(PrivateObjectKey.exportArtifact(WORKSPACE_ID, FILE_ID).value())
        .isEqualTo(
            "exports/10000000-0000-0000-0000-000000000001/"
                + "20000000-0000-0000-0000-000000000002/candidates.csv");

    assertThatThrownBy(() -> PrivateObjectKey.parse("../../candidate@example.com.pdf"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void streamsToAPrivateRootAndReturnsIntegrityMetadata() throws Exception {
    LocalObjectStorage storage = new LocalObjectStorage(root);
    PrivateObjectKey key = PrivateObjectKey.quarantineResume(WORKSPACE_ID, FILE_ID, VERSION_ID);

    StoredObject stored = storage.put(key, bytes("%PDF-demo"), 1024);

    assertThat(stored.key()).isEqualTo(key);
    assertThat(stored.sizeBytes()).isEqualTo(9);
    assertThat(stored.sha256Hex()).hasSize(64);
    try (var input = storage.open(key)) {
      assertThat(input.readAllBytes()).isEqualTo("%PDF-demo".getBytes(StandardCharsets.US_ASCII));
    }
  }

  @Test
  void rejectsOversizedObjectsWithoutLeavingPartialFiles() throws IOException {
    LocalObjectStorage storage = new LocalObjectStorage(root);
    PrivateObjectKey key = PrivateObjectKey.quarantineResume(WORKSPACE_ID, FILE_ID, VERSION_ID);

    assertThatThrownBy(() -> storage.put(key, bytes("123456"), 5))
        .isInstanceOf(ObjectSizeLimitException.class);
    assertThat(storage.exists(key)).isFalse();
    try (var paths = Files.walk(root)) {
      assertThat(paths.filter(Files::isRegularFile)).isEmpty();
    }
  }

  @Test
  void promotesOnlyQuarantineResumeIntoItsMatchingCleanKey() throws Exception {
    LocalObjectStorage storage = new LocalObjectStorage(root);
    PrivateObjectKey quarantine =
        PrivateObjectKey.quarantineResume(WORKSPACE_ID, FILE_ID, VERSION_ID);
    PrivateObjectKey clean = PrivateObjectKey.cleanResume(WORKSPACE_ID, FILE_ID, VERSION_ID);
    storage.put(quarantine, bytes("%PDF-demo"), 1024);

    storage.promote(quarantine, clean);

    assertThat(storage.exists(quarantine)).isFalse();
    assertThat(storage.exists(clean)).isTrue();
    assertThatThrownBy(
            () ->
                storage.promote(
                    PrivateObjectKey.importSource(WORKSPACE_ID, FILE_ID),
                    PrivateObjectKey.exportArtifact(WORKSPACE_ID, FILE_ID)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void deletesAnOpaqueImportObjectIdempotentlyWithoutTouchingSiblingObjects() {
    LocalObjectStorage storage = new LocalObjectStorage(root);
    PrivateObjectKey deleted = PrivateObjectKey.importSource(WORKSPACE_ID, FILE_ID);
    PrivateObjectKey sibling = PrivateObjectKey.exportArtifact(WORKSPACE_ID, FILE_ID);
    storage.put(deleted, bytes("first_name,email"), 1024);
    storage.put(sibling, bytes("synthetic export"), 1024);

    storage.delete(deleted);
    storage.delete(deleted);

    assertThat(storage.exists(deleted)).isFalse();
    assertThat(storage.exists(sibling)).isTrue();
  }

  private static ByteArrayInputStream bytes(String value) {
    return new ByteArrayInputStream(value.getBytes(StandardCharsets.US_ASCII));
  }
}
