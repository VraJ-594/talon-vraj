package com.talon.ats.files.application;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PrivateObjectKey {

  private static final String UUID_PATTERN =
      "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
  private static final Pattern RESUME =
      Pattern.compile(
          "^(quarantine|clean)/("
              + UUID_PATTERN
              + ")/resumes/("
              + UUID_PATTERN
              + ")/("
              + UUID_PATTERN
              + ")\\.pdf$");
  private static final Pattern IMPORT =
      Pattern.compile("^imports/(" + UUID_PATTERN + ")/(" + UUID_PATTERN + ")/source\\.csv$");
  private static final Pattern EXPORT =
      Pattern.compile("^exports/(" + UUID_PATTERN + ")/(" + UUID_PATTERN + ")/candidates\\.csv$");

  private final String value;
  private final Category category;

  private PrivateObjectKey(String value, Category category) {
    this.value = value;
    this.category = category;
  }

  public static PrivateObjectKey quarantineResume(UUID workspaceId, UUID fileId, UUID versionId) {
    return resume(Category.QUARANTINE_RESUME, workspaceId, fileId, versionId);
  }

  public static PrivateObjectKey cleanResume(UUID workspaceId, UUID fileId, UUID versionId) {
    return resume(Category.CLEAN_RESUME, workspaceId, fileId, versionId);
  }

  public static PrivateObjectKey importSource(UUID workspaceId, UUID importId) {
    return new PrivateObjectKey(
        "imports/" + required(workspaceId) + "/" + required(importId) + "/source.csv",
        Category.IMPORT_SOURCE);
  }

  public static PrivateObjectKey exportArtifact(UUID workspaceId, UUID exportId) {
    return new PrivateObjectKey(
        "exports/" + required(workspaceId) + "/" + required(exportId) + "/candidates.csv",
        Category.EXPORT_ARTIFACT);
  }

  public static PrivateObjectKey parse(String value) {
    Objects.requireNonNull(value, "value is required");
    Matcher resume = RESUME.matcher(value);
    if (resume.matches()) {
      Category category =
          resume.group(1).equals("quarantine") ? Category.QUARANTINE_RESUME : Category.CLEAN_RESUME;
      return new PrivateObjectKey(value, category);
    }
    if (IMPORT.matcher(value).matches()) {
      return new PrivateObjectKey(value, Category.IMPORT_SOURCE);
    }
    if (EXPORT.matcher(value).matches()) {
      return new PrivateObjectKey(value, Category.EXPORT_ARTIFACT);
    }
    throw new IllegalArgumentException("object key does not match an approved private category");
  }

  public String value() {
    return value;
  }

  public boolean isQuarantineResume() {
    return category == Category.QUARANTINE_RESUME;
  }

  public boolean isCleanResume() {
    return category == Category.CLEAN_RESUME;
  }

  public PrivateObjectKey cleanResumeKey() {
    if (!isQuarantineResume()) {
      throw new IllegalStateException("only quarantine resumes have a clean destination");
    }
    return parse("clean/" + value.substring("quarantine/".length()));
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || other instanceof PrivateObjectKey key
            && value.equals(key.value)
            && category == key.category;
  }

  @Override
  public int hashCode() {
    return Objects.hash(value, category);
  }

  @Override
  public String toString() {
    return value;
  }

  private static PrivateObjectKey resume(
      Category category, UUID workspaceId, UUID fileId, UUID versionId) {
    String prefix = category == Category.QUARANTINE_RESUME ? "quarantine" : "clean";
    return new PrivateObjectKey(
        prefix
            + "/"
            + required(workspaceId)
            + "/resumes/"
            + required(fileId)
            + "/"
            + required(versionId)
            + ".pdf",
        category);
  }

  private static UUID required(UUID value) {
    return Objects.requireNonNull(value, "object key identifiers are required");
  }

  private enum Category {
    QUARANTINE_RESUME,
    CLEAN_RESUME,
    IMPORT_SOURCE,
    EXPORT_ARTIFACT
  }
}
