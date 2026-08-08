package com.talon.ats.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.talon.ats.imports.application.CsvApplicationParser;
import com.talon.ats.imports.application.CsvInspection;
import com.talon.ats.imports.application.CsvParseException;
import com.talon.ats.imports.application.StrictTalonImportTemplate;
import com.talon.ats.imports.domain.CanonicalField;
import com.talon.ats.imports.domain.ColumnMapping;
import com.talon.ats.imports.infrastructure.csv.CommonsCsvApplicationParser;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StrictTalonImportTemplateTests {

  private static final String REQUIRED =
      "first_name,last_name,email,resume_drive_url\n"
          + "Nila,Raman,nila@example.com,https://drive.google.com/file/d/demo/view\n";

  private final CsvApplicationParser parser = new CommonsCsvApplicationParser();
  private final StrictTalonImportTemplate template = new StrictTalonImportTemplate();

  @Test
  void recognizesCanonicalHeadersCaseInsensitivelyWhilePreservingSourceLabels() {
    CsvInspection inspection =
        parser.inspect(
            stream(
                "FIRST_NAME,last_name,Email,resume_drive_url\n"
                    + "Nila,Raman,nila@example.com,https://drive.google.com/file/d/demo/view\n"));

    assertThat(template.recognize(inspection))
        .containsExactly(
            Map.entry("FIRST_NAME", CanonicalField.FIRST_NAME),
            Map.entry("last_name", CanonicalField.LAST_NAME),
            Map.entry("Email", CanonicalField.EMAIL),
            Map.entry("resume_drive_url", CanonicalField.RESUME_DRIVE_URL));
  }

  @Test
  void rejectsUnknownColumnsBeforeReturningARecognitionLedger() {
    CsvInspection inspection =
        parser.inspect(
            stream(
                "first_name,last_name,email,resume_drive_url,favorite_color\n"
                    + "Nila,Raman,nila@example.com,https://drive.google.com/file/d/demo/view,blue\n"));

    assertProblem("UNSUPPORTED_SOURCE_COLUMN", () -> template.recognize(inspection));
  }

  @Test
  void rejectsMissingRequiredColumns() {
    CsvInspection inspection =
        parser.inspect(stream("first_name,last_name,email\nNila,Raman,nila@example.com\n"));

    assertProblem("MISSING_REQUIRED_COLUMN", () -> template.recognize(inspection));
  }

  @Test
  void requiresTheServerRecognizedMappingAndRejectsRetainedUnknownAnswers() {
    CsvInspection inspection = parser.inspect(stream(REQUIRED));
    Map<String, CanonicalField> recognized = template.recognize(inspection);
    Map<String, CanonicalField> changed = new LinkedHashMap<>(recognized);
    changed.put("email", CanonicalField.PHONE);

    assertProblem(
        "MISSING_REQUIRED_MAPPING",
        () -> template.requireExactMapping(inspection.sourceColumns(), changed, false));
    assertProblem(
        "UNSUPPORTED_SOURCE_COLUMN",
        () -> template.requireExactMapping(inspection.sourceColumns(), recognized, true));

    ColumnMapping accepted =
        template.requireExactMapping(inspection.sourceColumns(), recognized, false);
    assertThat(accepted.assignments()).containsExactlyEntriesOf(recognized);
  }

  @Test
  void downloadsTheExactHeaderAndOneSyntheticExample() {
    String csv = new String(template.downloadBytes(), StandardCharsets.UTF_8);
    List<String> lines = csv.lines().toList();

    assertThat(lines)
        .hasSize(2)
        .first()
        .isEqualTo(
            "first_name,last_name,email,resume_drive_url,phone,location,"
                + "total_experience_years,current_company,current_title,skills,current_ctc,"
                + "expected_ctc,ctc_unit,ctc_currency,notice_period_days,availability_date,"
                + "source,application_date");
    assertThat(lines.get(1))
        .contains("candidate@example.com")
        .contains("https://drive.google.com/file/d/example/view");
    assertThat(parser.inspect(stream(csv)).rowCount()).isEqualTo(1);
  }

  private static void assertProblem(String code, Runnable action) {
    assertThatThrownBy(action::run)
        .isInstanceOf(CsvParseException.class)
        .extracting(error -> ((CsvParseException) error).code())
        .isEqualTo(code);
  }

  private static ByteArrayInputStream stream(String value) {
    return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
  }
}
