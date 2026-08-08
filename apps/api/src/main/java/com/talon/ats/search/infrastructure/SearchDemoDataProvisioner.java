package com.talon.ats.search.infrastructure;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

public final class SearchDemoDataProvisioner implements ApplicationRunner {

  private static final Logger LOGGER = LoggerFactory.getLogger(SearchDemoDataProvisioner.class);
  private static final UUID PLATFORM_JOB_ID =
      UUID.fromString("718a50d8-0aa1-4d72-b7f0-87ece8c20101");
  private static final UUID PRODUCT_JOB_ID =
      UUID.fromString("718a50d8-0aa1-4d72-b7f0-87ece8c20102");
  private static final List<DemoCandidate> CANDIDATES =
      List.of(
          candidate(
              "20101",
              "Asha",
              "Mehta",
              "Pune",
              "Senior Java Engineer",
              "Finch Labs",
              "Java, Spring Boot, PostgreSQL",
              96,
              30,
              320_000_000L,
              380_000_000L,
              PLATFORM_JOB_ID),
          candidate(
              "20102",
              "Rohan",
              "Iyer",
              "Bengaluru",
              "Platform Engineer",
              "Orbit Systems",
              "Java, Kubernetes, AWS",
              72,
              45,
              360_000_000L,
              420_000_000L,
              PLATFORM_JOB_ID),
          candidate(
              "20103",
              "Meera",
              "Shah",
              "Remote",
              "Frontend Engineer",
              "Canvas Works",
              "React, TypeScript, Design systems",
              60,
              15,
              240_000_000L,
              300_000_000L,
              PRODUCT_JOB_ID),
          candidate(
              "20104",
              "Kabir",
              "Khan",
              "Bengaluru",
              "Data Engineer",
              "Northstar Data",
              "Python, Kafka, PostgreSQL",
              48,
              60,
              280_000_000L,
              350_000_000L,
              PLATFORM_JOB_ID),
          candidate(
              "20105",
              "Nandini",
              "Rao",
              "Hyderabad",
              "Product Designer",
              "Mosaic Studio",
              "Figma, Research, Design systems",
              84,
              30,
              220_000_000L,
              280_000_000L,
              PRODUCT_JOB_ID));

  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;
  private final String workspaceSlug;

  public SearchDemoDataProvisioner(
      JdbcTemplate jdbc, TransactionTemplate transactions, String workspaceSlug) {
    this.jdbc = jdbc;
    this.transactions = transactions;
    this.workspaceSlug = workspaceSlug;
  }

  @Override
  public void run(ApplicationArguments arguments) {
    UUID workspaceId =
        jdbc
            .query(
                "SELECT id FROM workspace WHERE slug = ?",
                (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
                workspaceSlug)
            .stream()
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Demo workspace must be provisioned before search demo data"));
    transactions.executeWithoutResult(
        ignored -> {
          jdbc.queryForObject(
              "SELECT set_config('app.current_workspace_id', ?, true)",
              String.class,
              workspaceId.toString());
          jdbc.execute("SET LOCAL ROLE talon_app");
          upsertJob(
              workspaceId, PLATFORM_JOB_ID, "Senior Platform Engineer", "Engineering", "Pune");
          upsertJob(
              workspaceId, PRODUCT_JOB_ID, "Product Experience Designer", "Product", "Remote");
          CANDIDATES.forEach(candidate -> upsertCandidate(workspaceId, candidate));
        });
    LOGGER.info("Synthetic candidate search demo data provisioned");
  }

  private void upsertJob(
      UUID workspaceId, UUID jobId, String title, String department, String location) {
    jdbc.update(
        """
        INSERT INTO job(id, workspace_id, title, department_name, location, status)
        VALUES (?,?,?,?,?,'ACTIVE')
        ON CONFLICT (id) DO UPDATE SET
          title = EXCLUDED.title,
          department_name = EXCLUDED.department_name,
          location = EXCLUDED.location,
          status = 'ACTIVE',
          updated_at = now()
        WHERE job.workspace_id = EXCLUDED.workspace_id
        """,
        jobId,
        workspaceId,
        title,
        department,
        location);
  }

  private void upsertCandidate(UUID workspaceId, DemoCandidate demo) {
    jdbc.update(
        """
        INSERT INTO candidate(
          id, workspace_id, email, normalized_email, first_name, last_name, location,
          current_title, current_company, skills_text, experience_months)
        VALUES (?,?,?,?,?,?,?,?,?,?,?)
        ON CONFLICT (workspace_id, normalized_email) DO UPDATE SET
          first_name = EXCLUDED.first_name,
          last_name = EXCLUDED.last_name,
          location = EXCLUDED.location,
          current_title = EXCLUDED.current_title,
          current_company = EXCLUDED.current_company,
          skills_text = EXCLUDED.skills_text,
          experience_months = EXCLUDED.experience_months,
          updated_at = now()
        """,
        demo.candidateId(),
        workspaceId,
        demo.email(),
        demo.email(),
        demo.firstName(),
        demo.lastName(),
        demo.location(),
        demo.currentTitle(),
        demo.currentCompany(),
        demo.skills(),
        demo.experienceMonths());
    UUID candidateId =
        jdbc.queryForObject(
            "SELECT id FROM candidate WHERE workspace_id = ? AND normalized_email = ?",
            UUID.class,
            workspaceId,
            demo.email());
    jdbc.update(
        """
        INSERT INTO application(
          id, workspace_id, candidate_id, job_id, stage, status, source, applied_at,
          notice_days, available_from, current_ctc_currency, current_ctc_minor,
          expected_ctc_currency, expected_ctc_minor)
        VALUES (?,?,?,?,?,'ACTIVE','SEARCH_DEMO',?,?,?,?,?,?,?)
        ON CONFLICT (workspace_id, candidate_id, job_id) DO UPDATE SET
          stage = EXCLUDED.stage,
          status = 'ACTIVE',
          notice_days = EXCLUDED.notice_days,
          available_from = EXCLUDED.available_from,
          current_ctc_currency = EXCLUDED.current_ctc_currency,
          current_ctc_minor = EXCLUDED.current_ctc_minor,
          expected_ctc_currency = EXCLUDED.expected_ctc_currency,
          expected_ctc_minor = EXCLUDED.expected_ctc_minor,
          updated_at = now()
        """,
        demo.applicationId(),
        workspaceId,
        candidateId,
        demo.jobId(),
        "SCREENING",
        LocalDate.parse("2026-08-08").minusDays(demo.experienceMonths() % 5),
        demo.noticeDays(),
        LocalDate.parse("2026-09-15").plusDays(demo.noticeDays()),
        "INR",
        demo.currentCtcMinor(),
        "INR",
        demo.expectedCtcMinor());
  }

  private static DemoCandidate candidate(
      String suffix,
      String firstName,
      String lastName,
      String location,
      String currentTitle,
      String currentCompany,
      String skills,
      int experienceMonths,
      int noticeDays,
      long currentCtcMinor,
      long expectedCtcMinor,
      UUID jobId) {
    return new DemoCandidate(
        UUID.fromString("718a50d8-0aa1-4d72-b7f0-87ece8c" + suffix),
        UUID.fromString("818a50d8-0aa1-4d72-b7f0-87ece8c" + suffix),
        "search-demo-" + suffix + "@example.test",
        firstName,
        lastName,
        location,
        currentTitle,
        currentCompany,
        skills,
        experienceMonths,
        noticeDays,
        currentCtcMinor,
        expectedCtcMinor,
        jobId);
  }

  private record DemoCandidate(
      UUID candidateId,
      UUID applicationId,
      String email,
      String firstName,
      String lastName,
      String location,
      String currentTitle,
      String currentCompany,
      String skills,
      int experienceMonths,
      int noticeDays,
      long currentCtcMinor,
      long expectedCtcMinor,
      UUID jobId) {}
}
