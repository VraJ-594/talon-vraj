package com.talon.ats.imports.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class ColumnMapping {

  private final Map<String, CanonicalField> assignments;

  private ColumnMapping(Map<String, CanonicalField> assignments) {
    this.assignments = assignments;
  }

  public static ColumnMapping from(Map<String, CanonicalField> assignments) {
    Objects.requireNonNull(assignments, "assignments are required");
    Map<String, CanonicalField> normalized = new LinkedHashMap<>();
    Set<CanonicalField> assignedFields = EnumSet.noneOf(CanonicalField.class);
    for (Map.Entry<String, CanonicalField> entry : assignments.entrySet()) {
      String source = required(entry.getKey(), "source column");
      CanonicalField target =
          Objects.requireNonNull(entry.getValue(), "canonical field is required");
      if (!assignedFields.add(target)) {
        throw new IllegalArgumentException(
            "canonical field " + target.name() + " is assigned more than once");
      }
      normalized.put(source, target);
    }
    Set<CanonicalField> missing =
        EnumSet.allOf(CanonicalField.class).stream()
            .filter(CanonicalField::required)
            .filter(field -> !assignedFields.contains(field))
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(CanonicalField.class)));
    if (!missing.isEmpty()) {
      throw new IllegalArgumentException("required canonical mappings are missing: " + missing);
    }
    return new ColumnMapping(Collections.unmodifiableMap(new LinkedHashMap<>(normalized)));
  }

  public Map<String, CanonicalField> assignments() {
    return assignments;
  }

  private static String required(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return value.trim();
  }
}
