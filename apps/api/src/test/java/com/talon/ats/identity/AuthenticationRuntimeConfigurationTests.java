package com.talon.ats.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.talon.ats.identity.application.AuthenticationService;
import com.talon.ats.identity.application.IdentityAccountStore;
import com.talon.ats.identity.application.PasswordVerifier;
import com.talon.ats.identity.application.TokenIssuer;
import com.talon.ats.identity.infrastructure.security.AuthenticationRuntimeConfiguration;
import java.util.Base64;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.transaction.PlatformTransactionManager;

class AuthenticationRuntimeConfigurationTests {

  private static final DataSource DATA_SOURCE = dataSource();

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(AuthenticationRuntimeConfiguration.class)
          .withBean(DataSource.class, () -> DATA_SOURCE)
          .withBean(JdbcTemplate.class, () -> new JdbcTemplate(DATA_SOURCE))
          .withBean(
              PlatformTransactionManager.class,
              () -> new DataSourceTransactionManager(DATA_SOURCE));

  @Test
  void enablesCompleteAuthenticationRuntimeOnlyWithExplicitSecrets() {
    contextRunner
        .withPropertyValues(
            "talon.security.enabled=true",
            "talon.security.issuer=https://api.talon.example",
            "talon.security.audience=talon-web",
            "talon.security.access-signing-key=" + testKey('a'),
            "talon.security.refresh-hash-key=" + testKey('b'),
            "talon.security.access-token-lifetime=15m",
            "talon.security.refresh-token-lifetime=7d")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(AuthenticationService.class);
              assertThat(context).hasSingleBean(IdentityAccountStore.class);
              assertThat(context).hasSingleBean(PasswordVerifier.class);
              assertThat(context).hasSingleBean(TokenIssuer.class);
              assertThat(context).hasSingleBean(JwtDecoder.class);
            });
  }

  @Test
  void keepsAuthenticationRuntimeDisabledByDefault() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).doesNotHaveBean(AuthenticationService.class);
          assertThat(context).doesNotHaveBean(IdentityAccountStore.class);
          assertThat(context).doesNotHaveBean(JwtDecoder.class);
        });
  }

  @Test
  void rejectsSigningKeysShorterThan256Bits() {
    contextRunner
        .withPropertyValues(
            "talon.security.enabled=true",
            "talon.security.issuer=https://api.talon.example",
            "talon.security.audience=talon-web",
            "talon.security.access-signing-key="
                + Base64.getEncoder().encodeToString("too-short".getBytes()),
            "talon.security.refresh-hash-key=" + testKey('b'))
        .run(context -> assertThat(context).hasFailed());
  }

  private static DataSource dataSource() {
    return new DriverManagerDataSource("jdbc:postgresql://localhost:1/not-used", "none", "none");
  }

  private static String testKey(char value) {
    return Base64.getEncoder().encodeToString(String.valueOf(value).repeat(32).getBytes());
  }
}
