package com.talon.ats.search.infrastructure.postgres;

import com.talon.ats.search.application.AnnualCompensationView;
import com.talon.ats.search.application.CandidateSearchResult;
import com.talon.ats.search.application.CandidateSearchStore;
import com.talon.ats.search.application.CommandSearchItem;
import com.talon.ats.search.application.SearchResultSlice;
import com.talon.ats.search.application.SearchValidationException;
import com.talon.ats.search.domain.SearchCursor;
import com.talon.ats.search.domain.SearchField;
import com.talon.ats.search.domain.SearchOperator;
import com.talon.ats.search.domain.SearchSortField;
import com.talon.ats.search.domain.SortDirection;
import com.talon.ats.search.domain.ValidatedCandidateSearch;
import com.talon.ats.search.domain.ValidatedSearchPredicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

public final class PostgresCandidateSearchStore implements CandidateSearchStore {

  private static final String RESULT_SELECT =
      """
      SELECT a.id AS application_id, c.id AS candidate_id,
             concat_ws(' ', c.first_name, c.last_name) AS candidate_name,
             j.title AS job_title, a.stage, coalesce(c.location, '') AS location,
             coalesce(c.experience_months, 0) AS experience_months,
             coalesce(c.current_company, '') AS current_company,
             coalesce(c.current_title, '') AS current_title,
             coalesce(c.skills_text, '') AS skills_text,
             a.current_ctc_currency, a.current_ctc_minor,
             a.expected_ctc_currency, a.expected_ctc_minor,
             coalesce(a.notice_days, 0) AS notice_days, a.applied_at,
             coalesce(f.status, 'FAILED') AS resume_status
      FROM application a
      JOIN candidate c ON c.workspace_id = a.workspace_id AND c.id = a.candidate_id
      JOIN job j ON j.workspace_id = a.workspace_id AND j.id = a.job_id
      LEFT JOIN candidate_file f
        ON f.workspace_id = a.workspace_id AND f.application_id = a.id
      """;

  private final NamedParameterJdbcTemplate jdbc;
  private final TransactionTemplate transactions;

  public PostgresCandidateSearchStore(
      NamedParameterJdbcTemplate jdbc, TransactionTemplate transactions) {
    this.jdbc = jdbc;
    this.transactions = transactions;
  }

  @Override
  public SearchResultSlice search(UUID workspaceId, ValidatedCandidateSearch search) {
    return transactions.execute(
        ignored -> {
          setTenantContext(workspaceId);
          SqlQuery sql = compile(workspaceId, search);
          List<CandidateSearchResult> rows =
              jdbc.query(sql.text(), sql.parameters(), (resultSet, rowNumber) -> map(resultSet));
          boolean hasMore = rows.size() > search.limit();
          List<CandidateSearchResult> page =
              hasMore ? List.copyOf(rows.subList(0, search.limit())) : List.copyOf(rows);
          SearchCursor next = hasMore && !page.isEmpty() ? cursor(search, page.getLast()) : null;
          return new SearchResultSlice(page, next);
        });
  }

  @Override
  public List<CommandSearchItem> command(UUID workspaceId, String query, int limit) {
    return transactions.execute(
        ignored -> {
          setTenantContext(workspaceId);
          MapSqlParameterSource parameters =
              new MapSqlParameterSource()
                  .addValue("workspaceId", workspaceId)
                  .addValue("query", query)
                  .addValue("contains", "%" + escapeLike(query.toLowerCase(Locale.ROOT)) + "%")
                  .addValue("limit", limit);
          return jdbc.query(
              """
              WITH matches AS (
                SELECT 'CANDIDATE' AS type, c.id, latest.application_id,
                       concat_ws(' ', c.first_name, c.last_name) AS label,
                       concat_ws(' · ', nullif(c.current_title, ''), nullif(c.current_company, '')) AS description,
                       greatest(similarity(concat_ws(' ', c.first_name, c.last_name), :query),
                                similarity(c.normalized_email, :query)) AS score
                FROM candidate c
                LEFT JOIN LATERAL (
                  SELECT a.id AS application_id
                  FROM application a
                  WHERE a.workspace_id = c.workspace_id AND a.candidate_id = c.id
                  ORDER BY a.applied_at DESC, a.id
                  LIMIT 1
                ) latest ON true
                WHERE c.workspace_id = :workspaceId
                  AND (c.search_document @@ websearch_to_tsquery('simple', :query)
                       OR lower(concat_ws(' ', c.first_name, c.last_name)) LIKE :contains ESCAPE '\\'
                       OR c.normalized_email LIKE :contains ESCAPE '\\'
                       OR similarity(concat_ws(' ', c.first_name, c.last_name), :query) > 0.2)
                UNION ALL
                SELECT 'JOB', j.id, NULL::uuid, j.title,
                       concat_ws(' · ', nullif(j.department_name, ''), nullif(j.location, '')),
                       similarity(j.title, :query)
                FROM job j
                WHERE j.workspace_id = :workspaceId
                  AND j.status IN ('ACTIVE', 'ON_HOLD')
                  AND (lower(j.title) LIKE :contains ESCAPE '\\' OR similarity(j.title, :query) > 0.2)
              )
              SELECT type, id, application_id, label, description
              FROM matches
              ORDER BY score DESC, lower(label), id
              LIMIT :limit
              """,
              parameters,
              (resultSet, rowNumber) ->
                  new CommandSearchItem(
                      resultSet.getString("type"),
                      resultSet.getObject("id", UUID.class),
                      resultSet.getObject("application_id", UUID.class),
                      resultSet.getString("label"),
                      resultSet.getString("description")));
        });
  }

  private SqlQuery compile(UUID workspaceId, ValidatedCandidateSearch search) {
    StringBuilder sql = new StringBuilder(RESULT_SELECT);
    List<String> where = new ArrayList<>();
    Map<String, Object> parameters = new HashMap<>();
    where.add("a.workspace_id = :workspaceId");
    parameters.put("workspaceId", workspaceId);

    if (search.text() != null) {
      where.add(
          "(c.search_document @@ websearch_to_tsquery('simple', :text)"
              + " OR lower(concat_ws(' ', c.first_name, c.last_name)) LIKE :textContains ESCAPE '\\'"
              + " OR lower(c.skills_text) LIKE :textContains ESCAPE '\\'"
              + " OR similarity(concat_ws(' ', c.first_name, c.last_name), :text) > 0.2)");
      parameters.put("text", search.text());
      parameters.put(
          "textContains", "%" + escapeLike(search.text().toLowerCase(Locale.ROOT)) + "%");
    }

    for (int index = 0; index < search.predicates().size(); index++) {
      addPredicate(where, parameters, search.predicates().get(index), index);
    }
    addCursor(where, parameters, search);
    sql.append(" WHERE ").append(String.join(" AND ", where));
    sql.append(orderBy(search));
    sql.append(" LIMIT :rowLimit");
    parameters.put("rowLimit", search.limit() + 1);
    return new SqlQuery(sql.toString(), new MapSqlParameterSource(parameters));
  }

  private static void addPredicate(
      List<String> where,
      Map<String, Object> parameters,
      ValidatedSearchPredicate predicate,
      int index) {
    String key = "filter" + index;
    if (isText(predicate.field())) {
      String column = textColumn(predicate.field());
      if (predicate.operator() == SearchOperator.CONTAINS) {
        where.add("lower(" + column + ") LIKE :" + key + " ESCAPE '\\'");
        parameters.put(key, "%" + escapeLike(predicate.textValue().toLowerCase(Locale.ROOT)) + "%");
      } else {
        where.add("lower(" + column + ") = :" + key);
        parameters.put(key, predicate.textValue().toLowerCase(Locale.ROOT));
      }
      return;
    }
    if (predicate.field() == SearchField.CURRENT_COMPENSATION
        || predicate.field() == SearchField.EXPECTED_COMPENSATION) {
      String prefix =
          predicate.field() == SearchField.CURRENT_COMPENSATION
              ? "a.current_ctc"
              : "a.expected_ctc";
      where.add(prefix + "_currency = :" + key + "Currency");
      where.add(prefix + "_minor " + comparison(predicate.operator()) + " :" + key);
      parameters.put(key + "Currency", predicate.currency());
      parameters.put(key, predicate.numberValue());
      return;
    }
    String column = scalarColumn(predicate.field());
    where.add(column + " " + comparison(predicate.operator()) + " :" + key);
    parameters.put(
        key, predicate.dateValue() == null ? predicate.numberValue() : predicate.dateValue());
  }

  private static void addCursor(
      List<String> where, Map<String, Object> parameters, ValidatedCandidateSearch search) {
    if (search.cursor() == null) {
      return;
    }
    String comparator = search.sort().direction() == SortDirection.DESC ? "<" : ">";
    String column =
        search.sort().field() == SearchSortField.APPLIED_AT
            ? "a.applied_at"
            : "coalesce(c.experience_months, 0)";
    Object cursorValue;
    try {
      cursorValue =
          search.sort().field() == SearchSortField.APPLIED_AT
              ? LocalDate.parse(search.cursor().sortValue())
              : Integer.parseInt(search.cursor().sortValue());
    } catch (RuntimeException exception) {
      throw new SearchValidationException("CURSOR_INVALID", "Search cursor is invalid");
    }
    where.add(
        "("
            + column
            + " "
            + comparator
            + " :cursorValue OR ("
            + column
            + " = :cursorValue AND a.id "
            + comparator
            + " :cursorId))");
    parameters.put("cursorValue", cursorValue);
    parameters.put("cursorId", search.cursor().applicationId());
  }

  private static String orderBy(ValidatedCandidateSearch search) {
    String column =
        search.sort().field() == SearchSortField.APPLIED_AT
            ? "a.applied_at"
            : "coalesce(c.experience_months, 0)";
    String direction = search.sort().direction().name();
    return " ORDER BY " + column + " " + direction + ", a.id " + direction;
  }

  private static SearchCursor cursor(
      ValidatedCandidateSearch search, CandidateSearchResult result) {
    String value =
        search.sort().field() == SearchSortField.APPLIED_AT
            ? result.applicationDate().toString()
            : Integer.toString(result.totalExperienceMonths());
    return new SearchCursor(value, result.applicationId());
  }

  private static CandidateSearchResult map(java.sql.ResultSet resultSet)
      throws java.sql.SQLException {
    return new CandidateSearchResult(
        resultSet.getObject("application_id", UUID.class),
        resultSet.getObject("candidate_id", UUID.class),
        resultSet.getString("candidate_name"),
        resultSet.getString("job_title"),
        resultSet.getString("stage"),
        resultSet.getString("location"),
        resultSet.getInt("experience_months"),
        resultSet.getString("current_company"),
        resultSet.getString("current_title"),
        skills(resultSet.getString("skills_text")),
        compensation(
            resultSet.getString("current_ctc_currency"),
            (Long) resultSet.getObject("current_ctc_minor")),
        compensation(
            resultSet.getString("expected_ctc_currency"),
            (Long) resultSet.getObject("expected_ctc_minor")),
        resultSet.getInt("notice_days"),
        resultSet.getObject("applied_at", LocalDate.class),
        resumeStatus(resultSet.getString("resume_status")));
  }

  private static AnnualCompensationView compensation(String currency, Long minorUnits) {
    return currency == null || minorUnits == null
        ? null
        : new AnnualCompensationView(currency, minorUnits);
  }

  private static List<String> skills(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return java.util.Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(skill -> !skill.isEmpty())
        .limit(8)
        .toList();
  }

  private static String resumeStatus(String databaseStatus) {
    return switch (databaseStatus) {
      case "QUARANTINED" -> "RESUME_QUARANTINED";
      case "UNSAFE" -> "UNSAFE_FILE";
      default -> databaseStatus;
    };
  }

  private void setTenantContext(UUID workspaceId) {
    jdbc.queryForObject(
        "SELECT set_config('app.current_workspace_id', :workspaceId, true)",
        new MapSqlParameterSource("workspaceId", workspaceId.toString()),
        String.class);
    jdbc.getJdbcTemplate().execute("SET LOCAL ROLE talon_app");
  }

  private static boolean isText(SearchField field) {
    return switch (field) {
      case LOCATION, CURRENT_TITLE, CURRENT_COMPANY, SKILLS, APPLICATION_STAGE, JOB_TITLE, SOURCE ->
          true;
      default -> false;
    };
  }

  private static String textColumn(SearchField field) {
    return switch (field) {
      case LOCATION -> "c.location";
      case CURRENT_TITLE -> "c.current_title";
      case CURRENT_COMPANY -> "c.current_company";
      case SKILLS -> "c.skills_text";
      case APPLICATION_STAGE -> "a.stage";
      case JOB_TITLE -> "j.title";
      case SOURCE -> "a.source";
      default -> throw new IllegalArgumentException("not a text field");
    };
  }

  private static String scalarColumn(SearchField field) {
    return switch (field) {
      case EXPERIENCE_MONTHS -> "coalesce(c.experience_months, 0)";
      case NOTICE_PERIOD_DAYS -> "coalesce(a.notice_days, 0)";
      case APPLIED_AT -> "a.applied_at";
      case AVAILABLE_FROM -> "a.available_from";
      default -> throw new IllegalArgumentException("not a scalar field");
    };
  }

  private static String comparison(SearchOperator operator) {
    return switch (operator) {
      case EQUALS, ON -> "=";
      case GREATER_THAN -> ">";
      case GREATER_THAN_OR_EQUAL -> ">=";
      case LESS_THAN -> "<";
      case LESS_THAN_OR_EQUAL -> "<=";
      case BEFORE -> "<";
      case AFTER -> ">";
      default -> throw new IllegalArgumentException("not a comparison operator");
    };
  }

  private static String escapeLike(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private record SqlQuery(String text, MapSqlParameterSource parameters) {}
}
