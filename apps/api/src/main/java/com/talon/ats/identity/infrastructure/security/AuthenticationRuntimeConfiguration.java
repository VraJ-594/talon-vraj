package com.talon.ats.identity.infrastructure.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import com.talon.ats.identity.application.AuthenticationService;
import com.talon.ats.identity.application.BootstrapWorkspaceCommand;
import com.talon.ats.identity.application.IdentityAccountStore;
import com.talon.ats.identity.application.PasswordVerifier;
import com.talon.ats.identity.application.TokenIssuer;
import com.talon.ats.identity.application.WorkspaceBootstrapService;
import com.talon.ats.identity.infrastructure.persistence.JdbcIdentityAccountStore;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "talon.security.enabled", havingValue = "true")
@EnableConfigurationProperties(AuthenticationRuntimeProperties.class)
public class AuthenticationRuntimeConfiguration {

  @Bean
  JdbcIdentityAccountStore identityAccountStore(
      JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
    return new JdbcIdentityAccountStore(jdbc, new TransactionTemplate(transactionManager));
  }

  @Bean
  PasswordVerifier passwordVerifier() {
    return new BCryptPasswordVerifier(new BCryptPasswordEncoder(12));
  }

  @Bean
  JwtEncoder jwtEncoder(AuthenticationRuntimeProperties properties) {
    return new NimbusJwtEncoder(
        new ImmutableSecret<SecurityContext>(properties.accessSigningSecret()));
  }

  @Bean
  JwtDecoder jwtDecoder(AuthenticationRuntimeProperties properties) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(properties.accessSigningSecret())
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    OAuth2TokenValidator<Jwt> issuerValidator =
        JwtValidators.createDefaultWithIssuer(properties.issuer());
    OAuth2TokenValidator<Jwt> audienceValidator =
        new JwtClaimValidator<List<String>>(
            "aud", audience -> audience != null && audience.contains(properties.audience()));
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
    return decoder;
  }

  @Bean
  TokenIssuer tokenIssuer(JwtEncoder encoder, AuthenticationRuntimeProperties properties) {
    SecretKey refreshHashKey = properties.refreshHashSecret();
    return new JwtTokenIssuer(
        encoder, new SecureRandom(), refreshHashKey, properties.issuer(), properties.audience());
  }

  @Bean
  AuthenticationService authenticationService(
      IdentityAccountStore accountStore,
      PasswordVerifier passwordVerifier,
      TokenIssuer tokenIssuer,
      AuthenticationRuntimeProperties properties) {
    return new AuthenticationService(
        accountStore,
        passwordVerifier,
        tokenIssuer,
        UUID::randomUUID,
        Clock.systemUTC(),
        properties.accessTokenLifetime(),
        properties.refreshTokenLifetime());
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(name = "talon.demo-admin.enabled", havingValue = "true")
  @EnableConfigurationProperties(DemoAdminProperties.class)
  static class DemoAdminConfiguration {

    @Bean
    WorkspaceBootstrapService workspaceBootstrapService(JdbcIdentityAccountStore store) {
      return new WorkspaceBootstrapService(store, UUID::randomUUID, Clock.systemUTC());
    }

    @Bean
    DemoAdminProvisioner demoAdminProvisioner(
        WorkspaceBootstrapService service, DemoAdminProperties properties) {
      return new DemoAdminProvisioner(
          service,
          new BootstrapWorkspaceCommand(
              properties.email(),
              properties.displayName(),
              properties.passwordHash(),
              properties.workspaceName(),
              properties.workspaceSlug(),
              properties.defaultTimezone()));
    }
  }
}
