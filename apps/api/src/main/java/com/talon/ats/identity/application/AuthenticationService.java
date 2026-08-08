package com.talon.ats.identity.application;

import com.talon.ats.identity.domain.AppUserStatus;
import com.talon.ats.identity.domain.RefreshSession;
import com.talon.ats.identity.domain.WorkspaceMembershipStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class AuthenticationService {

  private static final String DUMMY_PASSWORD_HASH =
      "$2a$12$QdJ0g3Qm6T7ZxQ7CwoHk8eM65BmkBX3D.XqWmYxZyqFzKZb1AWc1S";

  private final IdentityAccountStore accountStore;
  private final PasswordVerifier passwordVerifier;
  private final TokenIssuer tokenIssuer;
  private final Supplier<UUID> idGenerator;
  private final Clock clock;
  private final Duration accessTokenLifetime;
  private final Duration refreshTokenLifetime;

  public AuthenticationService(
      IdentityAccountStore accountStore,
      PasswordVerifier passwordVerifier,
      TokenIssuer tokenIssuer,
      Supplier<UUID> idGenerator,
      Clock clock,
      Duration accessTokenLifetime,
      Duration refreshTokenLifetime) {
    this.accountStore = Objects.requireNonNull(accountStore);
    this.passwordVerifier = Objects.requireNonNull(passwordVerifier);
    this.tokenIssuer = Objects.requireNonNull(tokenIssuer);
    this.idGenerator = Objects.requireNonNull(idGenerator);
    this.clock = Objects.requireNonNull(clock);
    this.accessTokenLifetime = positive(accessTokenLifetime, "accessTokenLifetime");
    this.refreshTokenLifetime = positive(refreshTokenLifetime, "refreshTokenLifetime");
  }

  public AuthenticationResult authenticate(AuthenticateCommand command) {
    Objects.requireNonNull(command, "command is required");
    String normalizedEmail = normalizeEmail(command.email());
    String rawPassword = command.password() == null ? "" : command.password();
    Optional<AuthenticationAccount> possibleAccount =
        accountStore.findByNormalizedEmail(normalizedEmail);
    String passwordHash =
        possibleAccount.map(account -> account.user().passwordHash()).orElse(DUMMY_PASSWORD_HASH);
    boolean passwordMatches = passwordVerifier.matches(rawPassword, passwordHash);

    if (!passwordMatches || possibleAccount.isEmpty() || !isActive(possibleAccount.orElse(null))) {
      throw new AuthenticationFailedException();
    }

    AuthenticationAccount account = possibleAccount.orElseThrow();
    Instant issuedAt = clock.instant();
    Instant accessExpiresAt = issuedAt.plus(accessTokenLifetime);
    Instant refreshExpiresAt = issuedAt.plus(refreshTokenLifetime);
    TokenIssuer.AccessTokenClaims claims =
        new TokenIssuer.AccessTokenClaims(
            account.user().id(),
            account.membership().workspaceId(),
            account.workspaceName(),
            account.membership().role(),
            account.user().displayName(),
            issuedAt,
            accessExpiresAt);
    String accessToken = requiredToken(tokenIssuer.issueAccessToken(claims), "access token");
    String rawRefreshToken = requiredToken(tokenIssuer.issueRefreshToken(), "refresh token");
    String refreshTokenHash =
        requiredToken(tokenIssuer.hashRefreshToken(rawRefreshToken), "refresh token hash");
    if (rawRefreshToken.equals(refreshTokenHash)) {
      throw new IllegalStateException("refresh token must be stored as a non-reversible hash");
    }

    RefreshSession refreshSession =
        new RefreshSession(
            nextId(),
            account.user().id(),
            account.membership().workspaceId(),
            refreshTokenHash,
            nextId(),
            null,
            refreshExpiresAt,
            null,
            null,
            issuedAt);
    accountStore.completeSuccessfulLogin(refreshSession, issuedAt);

    return new AuthenticationResult(
        account.user().id(),
        account.membership().workspaceId(),
        account.workspaceName(),
        account.membership().role(),
        account.user().displayName(),
        accessToken,
        accessExpiresAt,
        rawRefreshToken,
        refreshExpiresAt);
  }

  public AuthenticationResult refresh(String rawRefreshToken) {
    if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
      throw new RefreshSessionRejectedException();
    }
    Instant issuedAt = clock.instant();
    Instant accessExpiresAt = issuedAt.plus(accessTokenLifetime);
    Instant refreshExpiresAt = issuedAt.plus(refreshTokenLifetime);
    String currentTokenHash =
        requiredToken(tokenIssuer.hashRefreshToken(rawRefreshToken), "refresh token hash");
    String nextRawRefreshToken = requiredToken(tokenIssuer.issueRefreshToken(), "refresh token");
    String nextTokenHash =
        requiredToken(tokenIssuer.hashRefreshToken(nextRawRefreshToken), "refresh token hash");
    if (nextRawRefreshToken.equals(nextTokenHash)) {
      throw new IllegalStateException("refresh token must be stored as a non-reversible hash");
    }

    AuthenticationAccount account =
        accountStore
            .rotateRefreshSession(
                currentTokenHash, nextId(), nextTokenHash, refreshExpiresAt, issuedAt)
            .filter(AuthenticationService::isActive)
            .orElseThrow(RefreshSessionRejectedException::new);
    String accessToken = issueAccessToken(account, issuedAt, accessExpiresAt);
    return result(account, accessToken, accessExpiresAt, nextRawRefreshToken, refreshExpiresAt);
  }

  public void logout(String rawRefreshToken) {
    if (rawRefreshToken == null || rawRefreshToken.isBlank()) return;
    String tokenHash =
        requiredToken(tokenIssuer.hashRefreshToken(rawRefreshToken), "refresh token hash");
    accountStore.revokeRefreshSessionFamily(tokenHash, clock.instant());
  }

  private String issueAccessToken(
      AuthenticationAccount account, Instant issuedAt, Instant accessExpiresAt) {
    TokenIssuer.AccessTokenClaims claims =
        new TokenIssuer.AccessTokenClaims(
            account.user().id(),
            account.membership().workspaceId(),
            account.workspaceName(),
            account.membership().role(),
            account.user().displayName(),
            issuedAt,
            accessExpiresAt);
    return requiredToken(tokenIssuer.issueAccessToken(claims), "access token");
  }

  private static AuthenticationResult result(
      AuthenticationAccount account,
      String accessToken,
      Instant accessExpiresAt,
      String rawRefreshToken,
      Instant refreshExpiresAt) {
    return new AuthenticationResult(
        account.user().id(),
        account.membership().workspaceId(),
        account.workspaceName(),
        account.membership().role(),
        account.user().displayName(),
        accessToken,
        accessExpiresAt,
        rawRefreshToken,
        refreshExpiresAt);
  }

  private static boolean isActive(AuthenticationAccount account) {
    return account != null
        && account.user().status() == AppUserStatus.ACTIVE
        && account.membership().status() == WorkspaceMembershipStatus.ACTIVE;
  }

  private static String normalizeEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
  }

  private UUID nextId() {
    return Objects.requireNonNull(idGenerator.get(), "generated ID is required");
  }

  private static Duration positive(Duration value, String field) {
    Objects.requireNonNull(value, field + " is required");
    if (value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException(field + " must be positive");
    }
    return value;
  }

  private static String requiredToken(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(field + " is required");
    }
    return value;
  }
}
