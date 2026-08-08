package com.talon.ats.search.infrastructure.groq;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.talon.ats.search.application.NaturalLanguageQuery;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GroqNaturalLanguageSearchInterpreterTests {

  @Test
  void sendsOnlyQueryContextAndSchemaThenParsesStrictDsl() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    AtomicReference<String> requestBody = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/chat/completions",
        exchange -> {
          requestBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          String content =
              """
              {"dslVersion":"1","text":null,"predicates":[{"field":"EXPECTED_COMPENSATION","operator":"LESS_THAN_OR_EQUAL","value":"400000000","currency":"INR"}],"sort":{"field":"APPLIED_AT","direction":"DESC"},"limit":25}
              """;
          byte[] response =
              objectMapper.writeValueAsBytes(
                  java.util.Map.of(
                      "choices",
                      java.util.List.of(
                          java.util.Map.of("message", java.util.Map.of("content", content)))));
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();
    try {
      var interpreter =
          new GroqNaturalLanguageSearchInterpreter(
              objectMapper,
              java.net.URI.create(
                  "http://127.0.0.1:" + server.getAddress().getPort() + "/chat/completions"),
              "test-key",
              "openai/gpt-oss-20b");

      var criteria =
          interpreter.interpret(
              new NaturalLanguageQuery("Candidates under 40 LPA", "en-IN", "Asia/Kolkata"));

      assertThat(criteria.predicates().getFirst().value()).isEqualTo("400000000");
      JsonNode sent = objectMapper.readTree(requestBody.get());
      assertThat(sent.path("model").asText()).isEqualTo("openai/gpt-oss-20b");
      assertThat(sent.path("response_format").path("json_schema").path("strict").asBoolean())
          .isTrue();
      assertThat(requestBody.get())
          .contains("Candidates under 40 LPA")
          .doesNotContain("candidateName", "resume", "workspaceId");
    } finally {
      server.stop(0);
    }
  }
}
