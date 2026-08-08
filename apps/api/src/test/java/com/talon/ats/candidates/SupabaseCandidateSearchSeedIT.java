package com.talon.ats.candidates;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class SupabaseCandidateSearchSeedIT {

  @Test
  void seedsThirtySixSyntheticApplicationsIdempotentlyAndRollsBack() throws Exception {
    Map<String, String> environment = System.getenv();
    String url = required(environment, "DATABASE_URL");
    String username = required(environment, "DATABASE_USERNAME");
    String password = required(environment, "DATABASE_PASSWORD");
    Flyway.configure()
        .dataSource(url, username, password)
        .locations("classpath:db/migration")
        .load()
        .migrate();
    DriverManagerDataSource dataSource = new DriverManagerDataSource(url, username, password);
    String slug = "candidate-seed-test-" + UUID.randomUUID();
    UUID workspaceId = UUID.randomUUID();
    String script =
        Files.readString(
                Path.of("..", "..", "scripts", "supabase", "seed-candidate-search-demo.sql"))
            .replace(
                "v_workspace_slug constant text := 'talon-demo'",
                "v_workspace_slug constant text := '" + slug + "'");

    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try {
        try (PreparedStatement insert =
            connection.prepareStatement(
                """
                INSERT INTO workspace(id,name,slug,default_timezone,status)
                VALUES (?,?,?,'UTC','ACTIVE')
                """)) {
          insert.setObject(1, workspaceId);
          insert.setString(2, "Candidate Seed Test");
          insert.setString(3, slug);
          insert.executeUpdate();
        }

        try (Statement statement = connection.createStatement()) {
          statement.execute(script);
          statement.execute(script);
        }

        assertThat(count(connection, "candidate", workspaceId)).isEqualTo(36);
        assertThat(count(connection, "application", workspaceId)).isEqualTo(36);
        assertThat(count(connection, "candidate_file", workspaceId)).isZero();
      } finally {
        connection.rollback();
      }
    }
  }

  private static int count(Connection connection, String table, UUID workspaceId) throws Exception {
    try (PreparedStatement query =
        connection.prepareStatement("SELECT count(*) FROM " + table + " WHERE workspace_id = ?")) {
      query.setObject(1, workspaceId);
      try (ResultSet rows = query.executeQuery()) {
        rows.next();
        return rows.getInt(1);
      }
    }
  }

  private static String required(Map<String, String> environment, String name) {
    String value = environment.get(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " is required");
    }
    return value;
  }
}
