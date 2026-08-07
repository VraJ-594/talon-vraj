package com.talon.ats.imports;

import static com.talon.ats.imports.domain.CanonicalField.APPLICATION_DATE;
import static com.talon.ats.imports.domain.CanonicalField.CTC_CURRENCY;
import static com.talon.ats.imports.domain.CanonicalField.CTC_UNIT;
import static com.talon.ats.imports.domain.CanonicalField.EMAIL;
import static com.talon.ats.imports.domain.CanonicalField.EXPECTED_CTC;
import static com.talon.ats.imports.domain.CanonicalField.FIRST_NAME;
import static com.talon.ats.imports.domain.CanonicalField.LAST_NAME;
import static com.talon.ats.imports.domain.CanonicalField.NOTICE_PERIOD_DAYS;
import static com.talon.ats.imports.domain.CanonicalField.RESUME_DRIVE_URL;
import static com.talon.ats.imports.domain.CanonicalField.TOTAL_EXPERIENCE_YEARS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.talon.ats.imports.application.CanonicalRowValidator;
import com.talon.ats.imports.application.RowValidationResult;
import com.talon.ats.imports.domain.CanonicalField;
import com.talon.ats.imports.domain.ColumnMapping;
import com.talon.ats.imports.domain.ImportJob;
import com.talon.ats.imports.domain.ImportStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImportMappingTests {

  private static final Instant NOW = Instant.parse("2026-08-07T19:00:00Z");

  @Test
  void requiresEveryCanonicalMappingNeededToCreateAnApplication() {
    Map<String, CanonicalField> incomplete = new LinkedHashMap<>();
    incomplete.put("First name", FIRST_NAME);
    incomplete.put("Last name", LAST_NAME);
    incomplete.put("Email", EMAIL);

    assertThatThrownBy(() -> ColumnMapping.from(incomplete))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("RESUME_DRIVE_URL");
  }

  @Test
  void preventsTwoSourceColumnsFromAssigningTheSameCanonicalField() {
    Map<String, CanonicalField> duplicate = requiredMapping();
    duplicate.put("Candidate email address", EMAIL);

    assertThatThrownBy(() -> ColumnMapping.from(duplicate))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("canonical field EMAIL is assigned more than once");
  }

  @Test
  void allowsTwoThousandRowsButRejectsTheNextRow() {
    ImportJob accepted = ImportJob.uploaded(id(1), id(2), id(3), 2_000, NOW);

    assertThat(accepted.status()).isEqualTo(ImportStatus.UPLOADED);
    assertThat(accepted.rowCount()).isEqualTo(2_000);
    assertThatThrownBy(() -> ImportJob.uploaded(id(1), id(2), id(3), 2_001, NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("rowCount must be between 1 and 2000");
  }

  @Test
  void normalizesEmailDatesExperienceNoticeAndLpaIntoTypedValues() {
    RowValidationResult result =
        new CanonicalRowValidator()
            .validate(
                2,
                Map.of(
                    FIRST_NAME, " Nila ",
                    LAST_NAME, " Raman ",
                    EMAIL, " Nila@Example.com ",
                    RESUME_DRIVE_URL, "https://drive.google.com/file/d/demo/view",
                    TOTAL_EXPERIENCE_YEARS, "6.5",
                    EXPECTED_CTC, "40",
                    CTC_UNIT, "LPA",
                    CTC_CURRENCY, "INR",
                    NOTICE_PERIOD_DAYS, "30",
                    APPLICATION_DATE, "2026-08-07"));

    assertThat(result.valid()).isTrue();
    assertThat(result.errors()).isEmpty();
    assertThat(result.row().orElseThrow().email()).isEqualTo("nila@example.com");
    assertThat(result.row().orElseThrow().firstName()).isEqualTo("Nila");
    assertThat(result.row().orElseThrow().experienceMonths()).isEqualTo(78);
    assertThat(result.row().orElseThrow().noticeDays()).isEqualTo(30);
    assertThat(result.row().orElseThrow().applicationDate())
        .isEqualTo(LocalDate.parse("2026-08-07"));
    assertThat(result.row().orElseThrow().expectedCompensation().minorUnits())
        .isEqualTo(400_000_000L);
    assertThat(result.row().orElseThrow().expectedCompensation().currency()).isEqualTo("INR");
  }

  @Test
  void reportsIndependentFieldErrorsWithoutProducingAPartialTypedRow() {
    RowValidationResult result =
        new CanonicalRowValidator()
            .validate(
                7,
                Map.of(
                    FIRST_NAME, "Nila",
                    LAST_NAME, "Raman",
                    EMAIL, "not-an-email",
                    RESUME_DRIVE_URL, "https://drive.google.com/file/d/demo/view",
                    TOTAL_EXPERIENCE_YEARS, "-1",
                    NOTICE_PERIOD_DAYS, "tomorrow",
                    APPLICATION_DATE, "07/08/2026"));

    assertThat(result.valid()).isFalse();
    assertThat(result.row()).isEmpty();
    assertThat(result.errors())
        .extracting(error -> error.code())
        .containsExactlyInAnyOrder(
            "INVALID_EMAIL", "INVALID_EXPERIENCE", "INVALID_NOTICE", "INVALID_DATE");
  }

  @Test
  void importStateTransitionsAreImmutableAndOrdered() {
    ImportJob uploaded = ImportJob.uploaded(id(1), id(2), id(3), 10, NOW);
    ImportJob mapped =
        uploaded.withMapping(ColumnMapping.from(requiredMapping()), NOW.plusSeconds(1));

    assertThat(uploaded.status()).isEqualTo(ImportStatus.UPLOADED);
    assertThat(mapped.status()).isEqualTo(ImportStatus.MAPPED);
    assertThatThrownBy(() -> uploaded.markPreviewReady(8, 2, NOW.plusSeconds(2)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("cannot transition import from UPLOADED to PREVIEW_READY");
  }

  private static Map<String, CanonicalField> requiredMapping() {
    Map<String, CanonicalField> mapping = new LinkedHashMap<>();
    mapping.put("First name", FIRST_NAME);
    mapping.put("Last name", LAST_NAME);
    mapping.put("Email", EMAIL);
    mapping.put("Resume", RESUME_DRIVE_URL);
    return mapping;
  }

  private static UUID id(long value) {
    return new UUID(0, value);
  }
}
