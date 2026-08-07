package com.talon.ats.imports.application;

import com.talon.ats.imports.domain.CanonicalField;
import java.util.List;
import java.util.Map;

public record CsvInspection(
    List<String> sourceColumns, int rowCount, Map<String, CanonicalField> suggestedMapping) {

  public CsvInspection {
    sourceColumns = List.copyOf(sourceColumns);
    suggestedMapping = Map.copyOf(suggestedMapping);
    if (rowCount < 1 || rowCount > 2_000) {
      throw new IllegalArgumentException("rowCount must be between 1 and 2000");
    }
  }
}
