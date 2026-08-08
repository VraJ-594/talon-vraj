package com.talon.ats.imports.infrastructure.csv;

import com.talon.ats.imports.application.CanonicalRowValidator;
import com.talon.ats.imports.application.CsvApplicationParser;
import com.talon.ats.imports.application.CsvInspection;
import com.talon.ats.imports.application.CsvParseException;
import com.talon.ats.imports.application.CsvParseResult;
import com.talon.ats.imports.application.CsvPreviewIssue;
import com.talon.ats.imports.application.ParsedApplicationRow;
import com.talon.ats.imports.application.RowValidationError;
import com.talon.ats.imports.application.RowValidationResult;
import com.talon.ats.imports.domain.CanonicalField;
import com.talon.ats.imports.domain.ColumnMapping;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.DuplicateHeaderMode;

public final class CommonsCsvApplicationParser implements CsvApplicationParser {

  private static final long MAX_BYTES = 10L * 1024 * 1024;
  private static final int MAX_ROWS = 2_000;
  private static final CSVFormat FORMAT =
      CSVFormat.RFC4180
          .builder()
          .setHeader()
          .setSkipHeaderRecord(true)
          .setAllowMissingColumnNames(false)
          .setDuplicateHeaderMode(DuplicateHeaderMode.ALLOW_ALL)
          .setIgnoreEmptyLines(false)
          .setLenientEof(false)
          .get();

  private final CanonicalRowValidator rowValidator;

  public CommonsCsvApplicationParser() {
    this(new CanonicalRowValidator());
  }

  CommonsCsvApplicationParser(CanonicalRowValidator rowValidator) {
    this.rowValidator = Objects.requireNonNull(rowValidator);
  }

  @Override
  public CsvInspection inspect(InputStream input) {
    try (CSVParser parser = parser(input)) {
      List<String> headers = headers(parser);
      int rowCount = 0;
      for (CSVRecord record : parser) {
        requireConsistent(record);
        rowCount++;
        requireRowLimit(rowCount);
      }
      if (rowCount == 0) {
        throw problem("INVALID_CSV", "CSV must contain at least one data row");
      }
      return new CsvInspection(headers, rowCount, suggestions(headers));
    } catch (CsvParseException exception) {
      throw exception;
    } catch (UncheckedIOException exception) {
      throw translate(exception.getCause());
    } catch (IOException | IllegalArgumentException exception) {
      throw translate(exception);
    }
  }

  @Override
  public CsvParseResult parse(InputStream input, ColumnMapping mapping, boolean retainUnmapped) {
    Objects.requireNonNull(mapping, "mapping is required");
    try (CSVParser parser = parser(input)) {
      List<String> headers = headers(parser);
      requireMappedHeaders(headers, mapping);
      List<ParsedApplicationRow> validRows = new ArrayList<>();
      List<CsvPreviewIssue> issues = new ArrayList<>();
      Set<String> acceptedEmails = new HashSet<>();
      int rowCount = 0;
      int invalidCount = 0;
      int duplicateCount = 0;
      for (CSVRecord record : parser) {
        requireConsistent(record);
        rowCount++;
        requireRowLimit(rowCount);
        int rowNumber = Math.toIntExact(record.getRecordNumber() + 1);
        RowValidationResult validation =
            rowValidator.validate(rowNumber, canonicalValues(record, mapping));
        if (!validation.valid()) {
          invalidCount++;
          for (RowValidationError error : validation.errors()) {
            issues.add(new CsvPreviewIssue(rowNumber, "INVALID", error.code(), error.message()));
          }
          continue;
        }
        if (!acceptedEmails.add(validation.row().orElseThrow().email())) {
          duplicateCount++;
          issues.add(
              new CsvPreviewIssue(
                  rowNumber,
                  "DUPLICATE",
                  "DUPLICATE_IN_FILE",
                  "candidate email is duplicated in this CSV"));
          continue;
        }
        validRows.add(
            new ParsedApplicationRow(
                validation.row().orElseThrow(),
                retainUnmapped ? additionalAnswers(record, headers, mapping) : Map.of()));
      }
      if (rowCount == 0) {
        throw problem("INVALID_CSV", "CSV must contain at least one data row");
      }
      return new CsvParseResult(validRows.size(), invalidCount, duplicateCount, validRows, issues);
    } catch (CsvParseException exception) {
      throw exception;
    } catch (UncheckedIOException exception) {
      throw translate(exception.getCause());
    } catch (IOException | IllegalArgumentException exception) {
      throw translate(exception);
    }
  }

  private static CSVParser parser(InputStream input) throws IOException {
    Objects.requireNonNull(input, "input is required");
    InputStream bounded = new SizeLimitedInputStream(input, MAX_BYTES);
    PushbackInputStream bomAware = new PushbackInputStream(bounded, 3);
    byte[] prefix = bomAware.readNBytes(3);
    if (!isUtf8Bom(prefix)) {
      bomAware.unread(prefix);
    }
    return CSVParser.parse(new InputStreamReader(bomAware, StandardCharsets.UTF_8), FORMAT);
  }

  private static List<String> headers(CSVParser parser) {
    List<String> headers = parser.getHeaderNames();
    if (headers.isEmpty()) {
      throw problem("INVALID_CSV", "CSV header is required");
    }
    Set<String> unique = new LinkedHashSet<>();
    for (String header : headers) {
      if (header == null || header.isBlank()) {
        throw problem("INVALID_CSV", "CSV header names must not be blank");
      }
      if (!unique.add(header.trim().toLowerCase(Locale.ROOT))) {
        throw problem("DUPLICATE_SOURCE_COLUMN", "CSV source columns must be unique");
      }
    }
    return List.copyOf(headers);
  }

  private static Map<String, CanonicalField> suggestions(List<String> headers) {
    Map<String, CanonicalField> suggestions = new LinkedHashMap<>();
    for (String header : headers) {
      String normalized = header.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
      try {
        suggestions.put(header, CanonicalField.valueOf(normalized));
      } catch (IllegalArgumentException ignored) {
        // Arbitrary Google Form headers are intentionally left for explicit mapping.
      }
    }
    return suggestions;
  }

  private static void requireMappedHeaders(List<String> headers, ColumnMapping mapping) {
    for (String source : mapping.assignments().keySet()) {
      if (!headers.contains(source)) {
        throw problem(
            "UNSUPPORTED_SOURCE_COLUMN", "mapped source column is not present in the CSV");
      }
    }
  }

  private static Map<CanonicalField, String> canonicalValues(
      CSVRecord record, ColumnMapping mapping) {
    Map<CanonicalField, String> values = new LinkedHashMap<>();
    mapping.assignments().forEach((source, target) -> values.put(target, record.get(source)));
    return values;
  }

  private static Map<String, String> additionalAnswers(
      CSVRecord record, List<String> headers, ColumnMapping mapping) {
    Map<String, String> answers = new LinkedHashMap<>();
    for (String header : headers) {
      if (!mapping.assignments().containsKey(header)) {
        answers.put(header, record.get(header));
      }
    }
    return answers;
  }

  private static void requireConsistent(CSVRecord record) {
    if (!record.isConsistent()) {
      throw problem("INVALID_CSV", "CSV row does not match the header column count");
    }
  }

  private static void requireRowLimit(int rowCount) {
    if (rowCount > MAX_ROWS) {
      throw problem("TOO_MANY_ROWS", "CSV cannot contain more than 2000 data rows");
    }
  }

  private static boolean isUtf8Bom(byte[] prefix) {
    return prefix.length == 3
        && prefix[0] == (byte) 0xEF
        && prefix[1] == (byte) 0xBB
        && prefix[2] == (byte) 0xBF;
  }

  private static CsvParseException translate(Throwable exception) {
    if (hasCause(exception, SizeLimitExceededException.class)) {
      return new CsvParseException("FILE_TOO_LARGE", "CSV cannot exceed 10 MB", exception);
    }
    return new CsvParseException("INVALID_CSV", "CSV could not be parsed", exception);
  }

  private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
    Throwable current = error;
    while (current != null) {
      if (type.isInstance(current)) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private static CsvParseException problem(String code, String message) {
    return new CsvParseException(code, message);
  }

  private static final class SizeLimitedInputStream extends FilterInputStream {

    private final long maximum;
    private long count;

    private SizeLimitedInputStream(InputStream input, long maximum) {
      super(input);
      this.maximum = maximum;
    }

    @Override
    public int read() throws IOException {
      int value = super.read();
      if (value != -1 && ++count > maximum) {
        throw new SizeLimitExceededException();
      }
      return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      int allowed = (int) Math.min(length, maximum - count + 1);
      int read = super.read(buffer, offset, Math.max(1, allowed));
      if (read > 0 && (count += read) > maximum) {
        throw new SizeLimitExceededException();
      }
      return read;
    }
  }

  private static final class SizeLimitExceededException extends IOException {}
}
