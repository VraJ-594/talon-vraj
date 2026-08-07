package com.talon.ats.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.talon.ats.identity.application.AuthenticateCommand;
import com.talon.ats.identity.application.AuthenticationAccount;
import com.talon.ats.identity.application.AuthenticationFailedException;
import com.talon.ats.identity.application.AuthenticationResult;
import com.talon.ats.identity.application.AuthenticationService;
import com.talon.ats.identity.application.IdentityAccountStore;
import com.talon.ats.identity.application.PasswordVerifier;
import com.talon.ats.identity.application.TokenIssuer;
import com.talon.ats.identity.domain.AppUser;
import com.talon.ats.identity.domain.AppUserStatus;
import com.talon.ats.identity.domain.RefreshSession;
import com.talon.ats.identity.domain.WorkspaceMembership;
import com.talon.ats.identity.domain.WorkspaceMembershipStatus;
import com.talon.ats.identity.domain.WorkspaceRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class AuthenticationServiceTests {

  private static final Instant NOW = Instant.parse("2026-08-07T10:00:00Z");
  private static final String PASSWORD_HASH =
      "$2a$12$WjJ0PmyFD6h7xjNQxJV13u98Ot4GY9NaAcNn0me04X6NxoJ/Oc9cW";

  @Test
  void authenticatesActiveAccountAndPersistsOnlyHashedRefreshToken() {
    RecordingAccountStore store = new RecordingAccountStore(Optional.of(activeAccount()));
    RecordingPasswordVerifier passwordVerifier = new RecordingPasswordVerifier(true);
    RecordingTokenIssuer tokenIssuer = new RecordingTokenIssuer();

    AuthenticationResult result =
        service(store, passwordVerifier, tokenIssuer)
            .authenticate(new AuthenticateCommand(" VRAJ@Example.com ", "correct-password"));

    assertThat(store.requestedEmail).isEqualTo("vraj@example.com");
    assertThat(passwordVerifier.rawPassword).isEqualTo("correct-password");
    assertThat(passwordVerifier.encodedPassword).isEqualTo(PASSWORD_HASH);
    assertThat(result.accessToken()).isEqualTo("signed-access-token");
    assertThat(result.accessTokenExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    assertThat(result.refreshToken()).isEqualTo("raw-refresh-token");
    assertThat(result.refreshTokenExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    assertThat(result.userId()).isEqualTo(uuid(1));
    assertThat(result.workspaceId()).isEqualTo(uuid(2));
    assertThat(result.role()).isEqualTo(WorkspaceRole.WORKSPACE_ADMIN);

    assertThat(store.savedSession.tokenHash()).isEqualTo("hashed-refresh-token");
    assertThat(store.savedSession.tokenHash()).doesNotContain(result.refreshToken());
    assertThat(store.savedSession.expiresAt()).isEqualTo(result.refreshTokenExpiresAt());
    assertThat(store.savedSession.createdAt()).isEqualTo(NOW);
    assertThat(store.loginRecordedAt).isEqualTo(NOW);
    assertThat(tokenIssuer.claims.userId()).isEqualTo(uuid(1));
    assertThat(tokenIssuer.claims.workspaceId()).isEqualTo(uuid(2));
    assertThat(tokenIssuer.claims.role()).isEqualTo(WorkspaceRole.WORKSPACE_ADMIN);
  }

  @Test
  void performsDummyPasswordVerificationAndReturnsGenericFailureForUnknownEmail() {
    RecordingAccountStore store = new RecordingAccountStore(Optional.empty());
    RecordingPasswordVerifier passwordVerifier = new RecordingPasswordVerifier(false);

    assertGenericFailure(
        () ->
            service(store, passwordVerifier, new RecordingTokenIssuer())
                .authenticate(new AuthenticateCommand("missing@example.com", "guess")));

    assertThat(passwordVerifier.rawPassword).isEqualTo("guess");
    assertThat(passwordVerifier.encodedPassword).startsWith("$2a$");
    assertThat(store.savedSession).isNull();
  }

  @Test
  void returnsSameGenericFailureForWrongPassword() {
    RecordingAccountStore store = new RecordingAccountStore(Optional.of(activeAccount()));

    assertGenericFailure(
        () ->
            service(store, new RecordingPasswordVerifier(false), new RecordingTokenIssuer())
                .authenticate(new AuthenticateCommand("vraj@example.com", "wrong")));

    assertThat(store.savedSession).isNull();
  }

  @Test
  void returnsSameGenericFailureForSuspendedAccountAfterPasswordVerification() {
    AuthenticationAccount suspended =
        new AuthenticationAccount(
            user(AppUserStatus.SUSPENDED), membership(WorkspaceMembershipStatus.ACTIVE));
    RecordingPasswordVerifier passwordVerifier = new RecordingPasswordVerifier(true);
    RecordingAccountStore store = new RecordingAccountStore(Optional.of(suspended));

    assertGenericFailure(
        () ->
            service(store, passwordVerifier, new RecordingTokenIssuer())
                .authenticate(new AuthenticateCommand("vraj@example.com", "correct-password")));

    assertThat(passwordVerifier.encodedPassword).isEqualTo(PASSWORD_HASH);
    assertThat(store.savedSession).isNull();
  }

  private AuthenticationService service(
      IdentityAccountStore store, PasswordVerifier passwordVerifier, TokenIssuer tokenIssuer) {
    Deque<UUID> ids = new ArrayDeque<>();
    ids.add(uuid(10));
    ids.add(uuid(11));
    Supplier<UUID> idGenerator = ids::removeFirst;
    return new AuthenticationService(
        store,
        passwordVerifier,
        tokenIssuer,
        idGenerator,
        Clock.fixed(NOW, ZoneOffset.UTC),
        Duration.ofMinutes(15),
        Duration.ofDays(7));
  }

  private static AuthenticationAccount activeAccount() {
    return new AuthenticationAccount(
        user(AppUserStatus.ACTIVE), membership(WorkspaceMembershipStatus.ACTIVE));
  }

  private static AppUser user(AppUserStatus status) {
    return new AppUser(
        uuid(1),
        "vraj@example.com",
        "vraj@example.com",
        "Vraj",
        PASSWORD_HASH,
        status,
        NOW.minus(Duration.ofDays(1)),
        null);
  }

  private static WorkspaceMembership membership(WorkspaceMembershipStatus status) {
    return new WorkspaceMembership(
        uuid(3), uuid(2), uuid(1), WorkspaceRole.WORKSPACE_ADMIN, status, NOW, 0);
  }

  private static void assertGenericFailure(Runnable operation) {
    assertThatThrownBy(operation::run)
        .isInstanceOf(AuthenticationFailedException.class)
        .hasMessage("Invalid email or password");
  }

  private static UUID uuid(long value) {
    return new UUID(0, value);
  }

  private static final class RecordingAccountStore implements IdentityAccountStore {
    private final Optional<AuthenticationAccount> account;
    private String requestedEmail;
    private RefreshSession savedSession;
    private Instant loginRecordedAt;

    private RecordingAccountStore(Optional<AuthenticationAccount> account) {
      this.account = account;
    }

    @Override
    public Optional<AuthenticationAccount> findByNormalizedEmail(String normalizedEmail) {
      requestedEmail = normalizedEmail;
      return account;
    }

    @Override
    public void completeSuccessfulLogin(RefreshSession refreshSession, Instant loggedInAt) {
      savedSession = refreshSession;
      loginRecordedAt = loggedInAt;
    }
  }

  private static final class RecordingPasswordVerifier implements PasswordVerifier {
    private final boolean result;
    private String rawPassword;
    private String encodedPassword;

    private RecordingPasswordVerifier(boolean result) {
      this.result = result;
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
      this.rawPassword = rawPassword;
      this.encodedPassword = encodedPassword;
      return result;
    }
  }

  private static final class RecordingTokenIssuer implements TokenIssuer {
    private AccessTokenClaims claims;

    @Override
    public String issueAccessToken(AccessTokenClaims claims) {
      this.claims = claims;
      return "signed-access-token";
    }

    @Override
    public String issueRefreshToken() {
      return "raw-refresh-token";
    }

    @Override
    public String hashRefreshToken(String rawRefreshToken) {
      assertThat(rawRefreshToken).isEqualTo("raw-refresh-token");
      return "hashed-refresh-token";
    }
  }
}
