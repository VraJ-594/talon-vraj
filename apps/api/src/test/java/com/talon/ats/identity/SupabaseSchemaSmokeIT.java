package com.talon.ats.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;

class SupabaseSchemaSmokeIT {

  @Test
  void migratesPrioritySchemaAndVerifiesTenantRole() throws Exception {
    Map<String, String> environment = System.getenv();
    String url = required(environment, "DATABASE_URL");
    String username = required(environment, "DATABASE_USERNAME");
    String password = required(environment, "DATABASE_PASSWORD");

    MigrateResult result =
        Flyway.configure()
            .dataSource(url, username, password)
            .locations("classpath:db/migration")
            .load()
            .migrate();

    assertThat(result.success).isTrue();
    try (Connection connection = DriverManager.getConnection(url, username, password);
        Statement statement = connection.createStatement()) {
      assertThat(exists(statement, "SELECT to_regclass('public.app_user') IS NOT NULL")).isTrue();
      assertThat(exists(statement, "SELECT to_regclass('public.refresh_session') IS NOT NULL"))
          .isTrue();
      assertThat(exists(statement, "SELECT to_regclass('public.candidate_import') IS NOT NULL"))
          .isTrue();
      assertThat(exists(statement, "SELECT to_regclass('public.candidate_import_row') IS NOT NULL"))
          .isTrue();
      assertThat(
              exists(
                  statement,
                  "SELECT to_regclass('public.candidate_search_document_gin_idx') IS NOT NULL"))
          .isTrue();
      assertThat(
              exists(
                  statement,
                  "SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='candidate' AND column_name='search_document')"))
          .isTrue();
      assertThat(
              exists(statement, "SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='talon_app')"))
          .isTrue();
      assertThat(
              exists(
                  statement,
                  "SELECT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='candidate_import')"))
          .isTrue();
      assertThat(
              exists(
                  statement,
                  "SELECT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='workspace')"))
          .isTrue();
    }
  }

  private static boolean exists(Statement statement, String sql) throws Exception {
    try (ResultSet resultSet = statement.executeQuery(sql)) {
      resultSet.next();
      return resultSet.getBoolean(1);
    }
  }

  private static String required(Map<String, String> environment, String name) {
    String value = environment.get(name);
    if (value == null || value.isBlank() || value.contains("<")) {
      throw new IllegalStateException(name + " must be supplied by the ignored .env.supabase file");
    }
    return value;
  }
}
