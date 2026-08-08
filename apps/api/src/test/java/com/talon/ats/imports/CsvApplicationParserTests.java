package com.talon.ats.imports;

import static com.talon.ats.imports.domain.CanonicalField.EMAIL;
import static com.talon.ats.imports.domain.CanonicalField.FIRST_NAME;
import static com.talon.ats.imports.domain.CanonicalField.LAST_NAME;
import static com.talon.ats.imports.domain.CanonicalField.RESUME_DRIVE_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.talon.ats.imports.application.CsvApplicationParser;
import com.talon.ats.imports.application.CsvInspection;
import com.talon.ats.imports.application.CsvParseException;
import com.talon.ats.imports.application.CsvParseResult;
import com.talon.ats.imports.domain.CanonicalField;
import com.talon.ats.imports.domain.ColumnMapping;
import com.talon.ats.imports.infrastructure.csv.CommonsCsvApplicationParser;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CsvApplicationParserTests {

  private final CsvApplicationParser parser = new CommonsCsvApplicationParser();

  @Test
  void inspectsBomHeadersRowsAndSafeCanonicalSuggestions() {
    String csv =
        "\uFEFFfirst_name,last_name,email,resume_drive_url,Team preference\r\n"
            + "Nila,Raman,nila@example.com,https://drive.google.com/file/d/demo/view,Platform\r\n";

    CsvInspection inspection = parser.inspect(stream(csv));

    assertThat(inspection.sourceColumns())
        .containsExactly("first_name", "last_name", "email", "resume_drive_url", "Team preference");
    assertThat(inspection.rowCount()).isEqualTo(1);
    assertThat(inspection.suggestedMapping())
        .containsEntry("first_name", FIRST_NAME)
        .containsEntry("resume_drive_url", RESUME_DRIVE_URL)
        .doesNotContainKey("Team preference");
  }

  @Test
  void streamsQuotedUtf8AndRetainsFormulaLikeAdditionalAnswersAsPlainData() {
    String csv =
        "\uFEFFFirst response,Last response,Email address,Resume link,Favorite note\r\n"
            + "\"Nila, A.\",Raman,nila@example.com,https://drive.google.com/file/d/demo/view,\"=SUM(1,1)\"\r\n";

    CsvParseResult result = parser.parse(stream(csv), mapping(), true);

    assertThat(result.validCount()).isEqualTo(1);
    assertThat(result.invalidCount()).isZero();
    assertThat(result.duplicateCount()).isZero();
    assertThat(result.validRows().getFirst().row().firstName()).isEqualTo("Nila, A.");
    assertThat(result.validRows().getFirst().additionalAnswers())
        .containsEntry("Favorite note", "=SUM(1,1)");
  }

  @Test
  void rejectsMalformedCsvAndDuplicateSourceHeadersWithStableCodes() {
    assertProblem(
        "First response,Last response,Email address,Resume link\r\n\"unterminated", "INVALID_CSV");
    assertProblem(
        "First response,Last response,Email address,Email address,Resume link\r\nNila,Raman,a@b.com,a@b.com,url",
        "DUPLICATE_SOURCE_COLUMN");
  }

  @Test
  void rejectsSourceHeadersThatDifferOnlyByCase() {
    assertThatThrownBy(
            () ->
                parser.inspect(
                    stream(
                        "first_name,last_name,email,EMAIL,resume_drive_url\n"
                            + "Nila,Raman,a@example.com,a@example.com,https://drive.google.com/file/d/demo/view\n")))
        .isInstanceOf(CsvParseException.class)
        .extracting(error -> ((CsvParseException) error).code())
        .isEqualTo("DUPLICATE_SOURCE_COLUMN");
  }

  @Test
  void ignoresOrRetainsUnmappedColumnsAccordingToTheExplicitChoice() {
    String csv =
        "First response,Last response,Email address,Resume link,Team preference\r\n"
            + "Nila,Raman,nila@example.com,https://drive.google.com/file/d/demo/view,Platform\r\n";

    CsvParseResult ignored = parser.parse(stream(csv), mapping(), false);
    CsvParseResult retained = parser.parse(stream(csv), mapping(), true);

    assertThat(ignored.validRows().getFirst().additionalAnswers()).isEmpty();
    assertThat(retained.validRows().getFirst().additionalAnswers())
        .containsExactlyEntriesOf(Map.of("Team preference", "Platform"));
  }

  @Test
  void separatesValidDuplicateAndInvalidRowsWithoutAbortingTheFile() {
    String csv =
        "First response,Last response,Email address,Resume link\r\n"
            + "Nila,Raman,nila@example.com,https://drive.google.com/file/d/one/view\r\n"
            + "Nila,Duplicate,NILA@example.com,https://drive.google.com/file/d/two/view\r\n"
            + "Bad,Email,not-an-email,https://drive.google.com/file/d/three/view\r\n";

    CsvParseResult result = parser.parse(stream(csv), mapping(), false);

    assertThat(result.validCount()).isEqualTo(1);
    assertThat(result.duplicateCount()).isEqualTo(1);
    assertThat(result.invalidCount()).isEqualTo(1);
    assertThat(result.issues())
        .extracting(issue -> issue.rowNumber() + ":" + issue.code())
        .containsExactly("3:DUPLICATE_IN_FILE", "4:INVALID_EMAIL");
  }

  @Test
  void rejectsMoreThanTwoThousandRows() {
    StringBuilder csv =
        new StringBuilder("First response,Last response,Email address,Resume link\n");
    for (int row = 1; row <= 2_001; row++) {
      csv.append("Nila,Raman,nila")
          .append(row)
          .append("@example.com,https://drive.google.com/file/d/")
          .append(row)
          .append("/view\n");
    }

    assertThatThrownBy(() -> parser.parse(stream(csv.toString()), mapping(), false))
        .isInstanceOf(CsvParseException.class)
        .extracting(error -> ((CsvParseException) error).code())
        .isEqualTo("TOO_MANY_ROWS");
  }

  @Test
  void rejectsInputLargerThanTenMegabytesWhileStreaming() {
    byte[] oversized = new byte[10 * 1024 * 1024 + 1];
    java.util.Arrays.fill(oversized, (byte) 'a');

    assertThatThrownBy(() -> parser.inspect(new ByteArrayInputStream(oversized)))
        .isInstanceOf(CsvParseException.class)
        .extracting(error -> ((CsvParseException) error).code())
        .isEqualTo("FILE_TOO_LARGE");
  }

  private void assertProblem(String csv, String code) {
    assertThatThrownBy(() -> parser.parse(stream(csv), mapping(), false))
        .isInstanceOf(CsvParseException.class)
        .extracting(error -> ((CsvParseException) error).code())
        .isEqualTo(code);
  }

  private static ColumnMapping mapping() {
    Map<String, CanonicalField> mapping = new LinkedHashMap<>();
    mapping.put("First response", FIRST_NAME);
    mapping.put("Last response", LAST_NAME);
    mapping.put("Email address", EMAIL);
    mapping.put("Resume link", RESUME_DRIVE_URL);
    return ColumnMapping.from(mapping);
  }

  private static ByteArrayInputStream stream(String value) {
    return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
  }
}
