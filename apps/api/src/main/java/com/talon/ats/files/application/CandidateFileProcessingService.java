package com.talon.ats.files.application;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;

public final class CandidateFileProcessingService {

  private final ObjectStorage storage;
  private final FileScanner scanner;
  private final ResumeTextExtractor extractor;

  public CandidateFileProcessingService(
      ObjectStorage storage, FileScanner scanner, ResumeTextExtractor extractor) {
    this.storage = Objects.requireNonNull(storage);
    this.scanner = Objects.requireNonNull(scanner);
    this.extractor = Objects.requireNonNull(extractor);
  }

  public ExtractedResumeText process(PrivateObjectKey quarantine, PrivateObjectKey clean) {
    requireMatchingKeys(quarantine, clean);
    FileScanVerdict verdict;
    try (InputStream input = storage.open(quarantine)) {
      verdict = Objects.requireNonNull(scanner.scan(input), "scanner verdict is required");
    } catch (RuntimeException | IOException exception) {
      throw new FileProcessingException("SCAN_FAILED", "Resume scan failed closed", exception);
    }
    if (verdict != FileScanVerdict.CLEAN) {
      throw new FileProcessingException("MALWARE_DETECTED", "Resume failed malware scanning");
    }

    storage.promote(quarantine, clean);
    try (InputStream input = storage.open(clean)) {
      return Objects.requireNonNull(extractor.extract(input), "extracted text is required");
    } catch (RuntimeException | IOException exception) {
      throw new FileProcessingException(
          "EXTRACTION_FAILED", "Resume text extraction failed", exception);
    }
  }

  public static PrivateObjectKey requireDownloadable(
      UUID requesterWorkspace,
      UUID ownerWorkspace,
      PrivateObjectKey key,
      FileScanVerdict scanVerdict) {
    Objects.requireNonNull(requesterWorkspace);
    Objects.requireNonNull(ownerWorkspace);
    Objects.requireNonNull(key);
    Objects.requireNonNull(scanVerdict);
    if (!requesterWorkspace.equals(ownerWorkspace)) {
      throw new FileProcessingException("FILE_NOT_FOUND", "Candidate file was not found");
    }
    if (scanVerdict != FileScanVerdict.CLEAN || !key.isCleanResume()) {
      throw new FileProcessingException(
          "FILE_NOT_DOWNLOADABLE", "Candidate file is not available for download");
    }
    return key;
  }

  private static void requireMatchingKeys(PrivateObjectKey quarantine, PrivateObjectKey clean) {
    Objects.requireNonNull(quarantine, "quarantine key is required");
    Objects.requireNonNull(clean, "clean key is required");
    if (!quarantine.isQuarantineResume() || !quarantine.cleanResumeKey().equals(clean)) {
      throw new IllegalArgumentException("processing requires matching quarantine and clean keys");
    }
  }
}
