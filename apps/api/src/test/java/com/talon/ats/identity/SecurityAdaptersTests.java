package com.talon.ats.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import com.talon.ats.identity.application.TokenIssuer.AccessTokenClaims;
import com.talon.ats.identity.contract.WorkspaceRole;
import com.talon.ats.identity.infrastructure.security.BCryptPasswordVerifier;
import com.talon.ats.identity.infrastructure.security.JwtTokenIssuer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class SecurityAdaptersTests {

  @Test
  void bcryptAdapterVerifiesWithoutOwningPasswordPolicy() {
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    BCryptPasswordVerifier verifier = new BCryptPasswordVerifier(encoder);
    String hash = encoder.encode("correct-password");

    assertThat(verifier.matches("correct-password", hash)).isTrue();
    assertThat(verifier.matches("wrong-password", hash)).isFalse();
  }

  @Test
  void jwtAdapterSignsScopedClaimsAndHashesOpaqueRefreshTokens() {
    SecretKey jwtKey = key("jwt-signing-key-at-least-32-bytes!");
    SecretKey refreshHashKey = key("refresh-hash-key-at-least-32-byte!");
    NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(jwtKey));
    JwtTokenIssuer issuer =
        new JwtTokenIssuer(
            encoder,
            new SecureRandom(new byte[] {1, 2, 3, 4}),
            refreshHashKey,
            "https://api.talon.local",
            "talon-web");
    Instant issuedAt = Instant.parse("2026-08-07T10:00:00Z");
    Instant expiresAt = Instant.parse("2026-08-07T10:15:00Z");
    UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID workspaceId = UUID.fromString("00000000-0000-0000-0000-000000000002");

    String token =
        issuer.issueAccessToken(
            new AccessTokenClaims(
                userId,
                workspaceId,
                "Talon Demo",
                WorkspaceRole.WORKSPACE_ADMIN,
                "Vraj",
                issuedAt,
                expiresAt));
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();
    JwtTimestampValidator timestampValidator = new JwtTimestampValidator();
    timestampValidator.setClock(Clock.fixed(issuedAt, ZoneOffset.UTC));
    decoder.setJwtValidator(timestampValidator);
    Jwt decoded = decoder.decode(token);

    assertThat(decoded.getSubject()).isEqualTo(userId.toString());
    assertThat(decoded.getIssuer().toString()).isEqualTo("https://api.talon.local");
    assertThat(decoded.getAudience()).containsExactly("talon-web");
    assertThat(decoded.getClaimAsString("workspace_id")).isEqualTo(workspaceId.toString());
    assertThat(decoded.getClaimAsString("workspace_name")).isEqualTo("Talon Demo");
    assertThat(decoded.getClaimAsString("role")).isEqualTo("WORKSPACE_ADMIN");
    assertThat(decoded.getClaimAsString("display_name")).isEqualTo("Vraj");
    assertThat(decoded.getIssuedAt()).isEqualTo(issuedAt);
    assertThat(decoded.getExpiresAt()).isEqualTo(expiresAt);

    String refreshToken = issuer.issueRefreshToken();
    assertThat(refreshToken).doesNotContain("=").hasSizeGreaterThanOrEqualTo(43);
    assertThat(issuer.hashRefreshToken(refreshToken))
        .isNotEqualTo(refreshToken)
        .isEqualTo(issuer.hashRefreshToken(refreshToken));

    assertThatThrownBy(
            () ->
                new JwtTokenIssuer(
                    encoder,
                    new SecureRandom(),
                    refreshHashKey,
                    "not-an-absolute-uri",
                    "talon-web"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("issuer must be an absolute URI");
  }

  private static SecretKey key(String value) {
    return new SecretKeySpec(value.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }
}
