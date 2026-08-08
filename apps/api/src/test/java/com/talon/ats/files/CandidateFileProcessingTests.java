package com.talon.ats.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.talon.ats.files.application.CandidateFileProcessingService;
import com.talon.ats.files.application.ExtractedResumeText;
import com.talon.ats.files.application.FileProcessingException;
import com.talon.ats.files.application.FileScanVerdict;
import com.talon.ats.files.application.ObjectStorage;
import com.talon.ats.files.application.PrivateObjectKey;
import com.talon.ats.files.application.StoredObject;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class CandidateFileProcessingTests {

  private static final UUID WORKSPACE = UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID FILE = UUID.fromString("20000000-0000-0000-0000-000000000002");
  private static final UUID VERSION = UUID.fromString("30000000-0000-0000-0000-000000000003");
  private static final PrivateObjectKey QUARANTINE =
      PrivateObjectKey.quarantineResume(WORKSPACE, FILE, VERSION);
  private static final PrivateObjectKey CLEAN =
      PrivateObjectKey.cleanResume(WORKSPACE, FILE, VERSION);

  @Test
  void promotesAndExtractsOnlyAfterACleanScan() {
    MemoryStorage storage = new MemoryStorage("%PDF-demo");
    CandidateFileProcessingService service =
        new CandidateFileProcessingService(
            storage,
            input -> FileScanVerdict.CLEAN,
            input -> new ExtractedResumeText("Senior Java engineer", 1, false));

    ExtractedResumeText result = service.process(QUARANTINE, CLEAN);

    assertThat(result.text()).isEqualTo("Senior Java engineer");
    assertThat(storage.exists(QUARANTINE)).isFalse();
    assertThat(storage.exists(CLEAN)).isTrue();
  }

  @Test
  void infectedOrFailedScansRemainQuarantinedAndNeverReachExtraction() {
    for (boolean scannerThrows : new boolean[] {false, true}) {
      MemoryStorage storage = new MemoryStorage("%PDF-demo");
      AtomicBoolean extracted = new AtomicBoolean();
      CandidateFileProcessingService service =
          new CandidateFileProcessingService(
              storage,
              input -> {
                if (scannerThrows) {
                  throw new IllegalStateException("scanner unavailable");
                }
                return FileScanVerdict.INFECTED;
              },
              input -> {
                extracted.set(true);
                return new ExtractedResumeText("must not run", 1, false);
              });

      assertThatThrownBy(() -> service.process(QUARANTINE, CLEAN))
          .isInstanceOfSatisfying(
              FileProcessingException.class,
              failure ->
                  assertThat(failure.code())
                      .isEqualTo(scannerThrows ? "SCAN_FAILED" : "MALWARE_DETECTED"));
      assertThat(storage.exists(QUARANTINE)).isTrue();
      assertThat(storage.exists(CLEAN)).isFalse();
      assertThat(extracted).isFalse();
    }
  }

  @Test
  void cleanDownloadPolicyRejectsQuarantineWrongWorkspaceAndNonCleanStatus() {
    assertThatThrownBy(
            () ->
                CandidateFileProcessingService.requireDownloadable(
                    WORKSPACE, WORKSPACE, QUARANTINE, FileScanVerdict.CLEAN))
        .isInstanceOfSatisfying(
            FileProcessingException.class,
            failure -> assertThat(failure.code()).isEqualTo("FILE_NOT_DOWNLOADABLE"));
    assertThatThrownBy(
            () ->
                CandidateFileProcessingService.requireDownloadable(
                    UUID.randomUUID(), WORKSPACE, CLEAN, FileScanVerdict.CLEAN))
        .isInstanceOfSatisfying(
            FileProcessingException.class,
            failure -> assertThat(failure.code()).isEqualTo("FILE_NOT_FOUND"));
    assertThatThrownBy(
            () ->
                CandidateFileProcessingService.requireDownloadable(
                    WORKSPACE, WORKSPACE, CLEAN, FileScanVerdict.INFECTED))
        .isInstanceOfSatisfying(
            FileProcessingException.class,
            failure -> assertThat(failure.code()).isEqualTo("FILE_NOT_DOWNLOADABLE"));

    assertThat(
            CandidateFileProcessingService.requireDownloadable(
                WORKSPACE, WORKSPACE, CLEAN, FileScanVerdict.CLEAN))
        .isEqualTo(CLEAN);
  }

  private static final class MemoryStorage implements ObjectStorage {

    private final Map<PrivateObjectKey, byte[]> objects = new HashMap<>();

    private MemoryStorage(String quarantine) {
      objects.put(QUARANTINE, quarantine.getBytes(StandardCharsets.US_ASCII));
    }

    @Override
    public StoredObject put(PrivateObjectKey key, InputStream input, long maximumBytes) {
      throw new UnsupportedOperationException();
    }

    @Override
    public InputStream open(PrivateObjectKey key) {
      return new ByteArrayInputStream(objects.get(key));
    }

    @Override
    public boolean exists(PrivateObjectKey key) {
      return objects.containsKey(key);
    }

    @Override
    public void delete(PrivateObjectKey key) {
      objects.remove(key);
    }

    @Override
    public void promote(PrivateObjectKey quarantine, PrivateObjectKey clean) {
      objects.put(clean, objects.remove(quarantine));
    }
  }
}
