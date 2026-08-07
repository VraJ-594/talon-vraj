package com.talon.ats.identity.infrastructure.security;

import com.talon.ats.identity.application.TokenIssuer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

public final class JwtTokenIssuer implements TokenIssuer {

  private static final int REFRESH_TOKEN_BYTES = 32;
  private static final String HMAC_SHA_256 = "HmacSHA256";

  private final JwtEncoder jwtEncoder;
  private final SecureRandom secureRandom;
  private final SecretKey refreshHashKey;
  private final String issuer;
  private final String audience;

  public JwtTokenIssuer(
      JwtEncoder jwtEncoder,
      SecureRandom secureRandom,
      SecretKey refreshHashKey,
      String issuer,
      String audience) {
    this.jwtEncoder = Objects.requireNonNull(jwtEncoder);
    this.secureRandom = Objects.requireNonNull(secureRandom);
    this.refreshHashKey = Objects.requireNonNull(refreshHashKey);
    this.issuer = absoluteUri(issuer);
    this.audience = required(audience, "audience");
  }

  @Override
  public String issueAccessToken(AccessTokenClaims claims) {
    Objects.requireNonNull(claims, "claims are required");
    JwtClaimsSet jwtClaims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .audience(List.of(audience))
            .subject(claims.userId().toString())
            .issuedAt(claims.issuedAt())
            .expiresAt(claims.expiresAt())
            .claim("workspace_id", claims.workspaceId().toString())
            .claim("role", claims.role().name())
            .claim("display_name", claims.displayName())
            .build();
    JwsHeader headers = JwsHeader.with(MacAlgorithm.HS256).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(headers, jwtClaims)).getTokenValue();
  }

  @Override
  public String issueRefreshToken() {
    byte[] token = new byte[REFRESH_TOKEN_BYTES];
    secureRandom.nextBytes(token);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
  }

  @Override
  public String hashRefreshToken(String rawRefreshToken) {
    try {
      Mac mac = Mac.getInstance(HMAC_SHA_256);
      mac.init(refreshHashKey);
      byte[] digest =
          mac.doFinal(
              required(rawRefreshToken, "rawRefreshToken").getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Unable to hash refresh token", exception);
    }
  }

  private static String required(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return value;
  }

  private static String absoluteUri(String value) {
    String issuer = required(value, "issuer");
    try {
      URI parsed = URI.create(issuer);
      if (!parsed.isAbsolute()) {
        throw new IllegalArgumentException("issuer must be an absolute URI");
      }
      return parsed.toString();
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("issuer must be an absolute URI", exception);
    }
  }
}
