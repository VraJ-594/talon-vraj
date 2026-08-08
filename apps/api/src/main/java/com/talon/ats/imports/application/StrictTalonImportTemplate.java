package com.talon.ats.imports.application;

import com.talon.ats.imports.domain.CanonicalField;
import com.talon.ats.imports.domain.ColumnMapping;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class StrictTalonImportTemplate {

  private static final List<String> HEADERS =
      List.of(
          "first_name",
          "last_name",
          "email",
          "resume_drive_url",
          "phone",
          "location",
          "total_experience_years",
          "current_company",
          "current_title",
          "skills",
          "current_ctc",
          "expected_ctc",
          "ctc_unit",
          "ctc_currency",
          "notice_period_days",
          "availability_date",
          "source",
          "application_date");

  private static final Map<String, CanonicalField> FIELDS = fields();
  private static final String EXAMPLE =
      "Nila,Raman,candidate@example.com,https://drive.google.com/file/d/example/view,"
          + "+91-9000000000,Pune,5.5,Talon,Engineer,Java; PostgreSQL,30,40,LPA,INR,30,"
          + "2026-09-01,GOOGLE_FORM,2026-08-08";

  public List<String> canonicalHeaders() {
    return HEADERS;
  }

  public byte[] downloadBytes() {
    return (String.join(",", HEADERS) + "\r\n" + EXAMPLE + "\r\n").getBytes(StandardCharsets.UTF_8);
  }

  public Map<String, CanonicalField> recognize(CsvInspection inspection) {
    if (inspection == null) {
      throw new IllegalArgumentException("inspection is required");
    }
    return recognize(inspection.sourceColumns());
  }

  public ColumnMapping requireExactMapping(
      List<String> sourceColumns, Map<String, CanonicalField> requested, boolean retainUnmapped) {
    if (retainUnmapped) {
      throw problem(
          "UNSUPPORTED_SOURCE_COLUMN", "Strict Talon imports do not retain unmapped columns");
    }
    if (requested == null) {
      throw problem("MISSING_REQUIRED_MAPPING", "The recognized column mapping is required");
    }

    Map<String, CanonicalField> recognized = recognize(sourceColumns);
    Set<CanonicalField> assigned = EnumSet.noneOf(CanonicalField.class);
    for (Map.Entry<String, CanonicalField> entry : requested.entrySet()) {
      if (!recognized.containsKey(entry.getKey())) {
        throw problem("UNSUPPORTED_SOURCE_COLUMN", "The mapping contains an unknown source column");
      }
      if (entry.getValue() == null || !assigned.add(entry.getValue())) {
        throw problem("DUPLICATE_MAPPING", "Each canonical field may be mapped only once");
      }
    }

    boolean requiredMissing =
        EnumSet.allOf(CanonicalField.class).stream()
            .filter(CanonicalField::required)
            .anyMatch(field -> !assigned.contains(field));
    if (requiredMissing) {
      throw problem("MISSING_REQUIRED_MAPPING", "Required canonical mappings are missing");
    }
    if (!recognized.equals(requested)) {
      throw problem("UNSUPPORTED_SOURCE_COLUMN", "The mapping must match the recognized columns");
    }
    return ColumnMapping.from(requested);
  }

  private static Map<String, CanonicalField> recognize(List<String> sourceColumns) {
    if (sourceColumns == null) {
      throw new IllegalArgumentException("sourceColumns are required");
    }
    Map<String, CanonicalField> recognized = new LinkedHashMap<>();
    Set<String> normalizedSources = new LinkedHashSet<>();
    for (String source : sourceColumns) {
      if (source == null || source.isBlank()) {
        throw problem("INVALID_CSV", "CSV header names must not be blank");
      }
      String normalized = source.trim().toLowerCase(Locale.ROOT);
      if (!normalizedSources.add(normalized)) {
        throw problem("DUPLICATE_SOURCE_COLUMN", "CSV source columns must be unique");
      }
      CanonicalField field = FIELDS.get(normalized);
      if (field == null) {
        throw problem(
            "UNSUPPORTED_SOURCE_COLUMN", "CSV contains a column outside the Talon template");
      }
      recognized.put(source, field);
    }

    boolean requiredMissing =
        EnumSet.allOf(CanonicalField.class).stream()
            .filter(CanonicalField::required)
            .anyMatch(field -> !recognized.containsValue(field));
    if (requiredMissing) {
      throw problem("MISSING_REQUIRED_COLUMN", "CSV is missing a required Talon column");
    }
    return Collections.unmodifiableMap(new LinkedHashMap<>(recognized));
  }

  private static Map<String, CanonicalField> fields() {
    Map<String, CanonicalField> fields = new LinkedHashMap<>();
    for (String header : HEADERS) {
      fields.put(header, CanonicalField.valueOf(header.toUpperCase(Locale.ROOT)));
    }
    return Map.copyOf(fields);
  }

  private static CsvParseException problem(String code, String message) {
    return new CsvParseException(code, message);
  }
}
