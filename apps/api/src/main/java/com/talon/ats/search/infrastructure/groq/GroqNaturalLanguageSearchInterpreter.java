package com.talon.ats.search.infrastructure.groq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talon.ats.search.application.NaturalLanguageQuery;
import com.talon.ats.search.application.NaturalLanguageSearchInterpreter;
import com.talon.ats.search.application.SearchInterpreterException;
import com.talon.ats.search.domain.CandidateSearchCriteria;
import com.talon.ats.search.domain.SearchField;
import com.talon.ats.search.domain.SearchOperator;
import com.talon.ats.search.domain.SearchPredicate;
import com.talon.ats.search.domain.SearchSort;
import com.talon.ats.search.domain.SearchSortField;
import com.talon.ats.search.domain.SortDirection;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GroqNaturalLanguageSearchInterpreter
    implements NaturalLanguageSearchInterpreter {

  private static final Duration TIMEOUT = Duration.ofSeconds(3);
  private static final String SYSTEM_PROMPT =
      """
      Translate an ATS recruiter query into the supplied candidate_search JSON schema.
      Never invent unsupported fields. Keep unstructured keywords in text. Use ISO dates.
      Convert experience years to whole months. LPA always means annual INR: multiply lakhs by
      100,000 rupees and then by 100 paise. Put compensation paise in value and INR in currency.
      Do not infer or convert another currency. Default to APPLIED_AT DESC and limit 25.
      """;

  private final ObjectMapper objectMapper;
  private final HttpClient client;
  private final URI endpoint;
  private final String apiKey;
  private final String model;

  public GroqNaturalLanguageSearchInterpreter(
      ObjectMapper objectMapper, URI endpoint, String apiKey, String model) {
    this.objectMapper = objectMapper;
    this.client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    this.endpoint = endpoint;
    this.apiKey = apiKey;
    this.model = model;
  }

  @Override
  public CandidateSearchCriteria interpret(NaturalLanguageQuery query) {
    HttpRequest request =
        HttpRequest.newBuilder(endpoint)
            .timeout(TIMEOUT)
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody(query)))
            .build();
    try {
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 429) {
        throw new SearchInterpreterException(
            "INTERPRETER_QUOTA_EXCEEDED", "AI interpretation quota is unavailable");
      }
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new SearchInterpreterException(
            "INTERPRETER_UNAVAILABLE", "AI interpretation is temporarily unavailable");
      }
      return parse(response.body());
    } catch (java.net.http.HttpTimeoutException exception) {
      throw new SearchInterpreterException(
          "INTERPRETER_UNAVAILABLE", "AI interpretation timed out", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new SearchInterpreterException(
          "INTERPRETER_UNAVAILABLE", "AI interpretation was interrupted", exception);
    } catch (IOException exception) {
      throw new SearchInterpreterException(
          "INTERPRETER_UNAVAILABLE", "AI interpretation is temporarily unavailable", exception);
    }
  }

  private String requestBody(NaturalLanguageQuery query) {
    Map<String, Object> context =
        Map.of(
            "query", query.query(),
            "locale", safe(query.locale()),
            "timezone", safe(query.timezone()),
            "today", LocalDate.now().toString());
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", model);
    body.put(
        "messages",
        List.of(
            Map.of("role", "system", "content", SYSTEM_PROMPT),
            Map.of("role", "user", "content", json(context))));
    body.put("temperature", 0);
    body.put("max_completion_tokens", 800);
    body.put("response_format", responseFormat());
    return json(body);
  }

  private CandidateSearchCriteria parse(String responseBody) {
    try {
      JsonNode root = objectMapper.readTree(responseBody);
      JsonNode content = root.path("choices").path(0).path("message").path("content");
      if (!content.isTextual()) {
        throw invalidResponse();
      }
      JsonNode result = objectMapper.readTree(content.asText());
      requireOnly(result, "dslVersion", "text", "predicates", "sort", "limit");
      List<SearchPredicate> predicates =
          stream(result.path("predicates")).map(this::predicate).toList();
      JsonNode sort = result.path("sort");
      requireOnly(sort, "field", "direction");
      return new CandidateSearchCriteria(
          result.path("dslVersion").asText(),
          nullableText(result.get("text")),
          predicates,
          new SearchSort(
              SearchSortField.valueOf(sort.path("field").asText()),
              SortDirection.valueOf(sort.path("direction").asText())),
          result.path("limit").asInt(),
          null);
    } catch (RuntimeException | JsonProcessingException exception) {
      if (exception instanceof SearchInterpreterException interpreterException) {
        throw interpreterException;
      }
      throw new SearchInterpreterException(
          "INTERPRETATION_INVALID", "AI returned an invalid search interpretation", exception);
    }
  }

  private SearchPredicate predicate(JsonNode node) {
    requireOnly(node, "field", "operator", "value", "currency");
    return new SearchPredicate(
        SearchField.valueOf(node.path("field").asText()),
        SearchOperator.valueOf(node.path("operator").asText()),
        node.path("value").asText(),
        nullableText(node.get("currency")));
  }

  private static java.util.stream.Stream<JsonNode> stream(JsonNode array) {
    if (!array.isArray()) {
      throw invalidResponse();
    }
    return java.util.stream.StreamSupport.stream(array.spliterator(), false);
  }

  private static void requireOnly(JsonNode object, String... fields) {
    if (!object.isObject()) {
      throw invalidResponse();
    }
    java.util.Set<String> allowed = java.util.Set.of(fields);
    object
        .fieldNames()
        .forEachRemaining(
            field -> {
              if (!allowed.contains(field)) {
                throw invalidResponse();
              }
            });
    for (String field : fields) {
      if (!object.has(field)) {
        throw invalidResponse();
      }
    }
  }

  private Map<String, Object> responseFormat() {
    Map<String, Object> nullableString = Map.of("type", List.of("string", "null"));
    Map<String, Object> predicate =
        Map.of(
            "type",
            "object",
            "properties",
            Map.of(
                "field", Map.of("type", "string", "enum", enumNames(SearchField.values())),
                "operator", Map.of("type", "string", "enum", enumNames(SearchOperator.values())),
                "value", Map.of("type", "string"),
                "currency", nullableString),
            "required",
            List.of("field", "operator", "value", "currency"),
            "additionalProperties",
            false);
    Map<String, Object> sort =
        Map.of(
            "type",
            "object",
            "properties",
            Map.of(
                "field", Map.of("type", "string", "enum", enumNames(SearchSortField.values())),
                "direction", Map.of("type", "string", "enum", enumNames(SortDirection.values()))),
            "required",
            List.of("field", "direction"),
            "additionalProperties",
            false);
    Map<String, Object> schema =
        Map.of(
            "type",
            "object",
            "properties",
            Map.of(
                "dslVersion", Map.of("type", "string", "enum", List.of("1")),
                "text", nullableString,
                "predicates", Map.of("type", "array", "maxItems", 12, "items", predicate),
                "sort", sort,
                "limit", Map.of("type", "integer", "minimum", 1, "maximum", 100)),
            "required",
            List.of("dslVersion", "text", "predicates", "sort", "limit"),
            "additionalProperties",
            false);
    return Map.of(
        "type",
        "json_schema",
        "json_schema",
        Map.of("name", "candidate_search", "strict", true, "schema", schema));
  }

  private static List<String> enumNames(Enum<?>[] values) {
    return java.util.Arrays.stream(values).map(Enum::name).toList();
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("search schema could not be serialized", exception);
    }
  }

  private static String nullableText(JsonNode value) {
    return value == null || value.isNull() ? null : value.asText();
  }

  private static String safe(String value) {
    return value == null ? "" : value.trim();
  }

  private static SearchInterpreterException invalidResponse() {
    return new SearchInterpreterException(
        "INTERPRETATION_INVALID", "AI returned an invalid search interpretation");
  }
}
