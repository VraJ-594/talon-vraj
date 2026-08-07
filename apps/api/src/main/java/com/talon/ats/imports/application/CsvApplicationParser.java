package com.talon.ats.imports.application;

import com.talon.ats.imports.domain.ColumnMapping;
import java.io.InputStream;

public interface CsvApplicationParser {

  CsvInspection inspect(InputStream input);

  CsvParseResult parse(InputStream input, ColumnMapping mapping, boolean retainUnmapped);
}
