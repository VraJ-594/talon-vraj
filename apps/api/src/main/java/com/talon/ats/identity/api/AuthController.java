package com.talon.ats.identity.api;

import com.talon.ats.identity.application.AuthenticateCommand;
import com.talon.ats.identity.application.AuthenticationResult;
import com.talon.ats.identity.application.AuthenticationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "talon.security.enabled", havingValue = "true")
public class AuthController {

  static final String REFRESH_COOKIE_NAME = "talon_refresh";

  private final AuthenticationService authenticationService;
  private final boolean secureCookie;

  public AuthController(
      AuthenticationService authenticationService,
      @Value("${talon.security.cookie-secure:true}") boolean secureCookie) {
    this.authenticationService = authenticationService;
    this.secureCookie = secureCookie;
  }

  @PostMapping("/auth/login")
  ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    AuthenticationResult result =
        authenticationService.authenticate(
            new AuthenticateCommand(request.email(), request.password()));
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString())
        .body(LoginResponse.from(result));
  }

  @PostMapping("/auth/refresh")
  ResponseEntity<LoginResponse> refresh(
      @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken) {
    AuthenticationResult result = authenticationService.refresh(refreshToken);
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString())
        .body(LoginResponse.from(result));
  }

  @PostMapping("/auth/logout")
  ResponseEntity<Void> logout(
      @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken) {
    authenticationService.logout(refreshToken);
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie().toString())
        .build();
  }

  @GetMapping("/session")
  SessionResponse session(@AuthenticationPrincipal Jwt jwt) {
    return new SessionResponse(
        UUID.fromString(jwt.getSubject()),
        UUID.fromString(jwt.getClaimAsString("workspace_id")),
        jwt.getClaimAsString("workspace_name"),
        jwt.getClaimAsString("role"),
        jwt.getClaimAsString("display_name"));
  }

  record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

  record LoginResponse(
      UUID userId,
      UUID workspaceId,
      String workspaceName,
      String role,
      String displayName,
      String accessToken,
      Instant accessTokenExpiresAt) {

    static LoginResponse from(AuthenticationResult result) {
      return new LoginResponse(
          result.userId(),
          result.workspaceId(),
          result.workspaceName(),
          result.role().name(),
          result.displayName(),
          result.accessToken(),
          result.accessTokenExpiresAt());
    }
  }

  record SessionResponse(
      UUID userId, UUID workspaceId, String workspaceName, String role, String displayName) {}

  private ResponseCookie refreshCookie(String value) {
    return ResponseCookie.from(REFRESH_COOKIE_NAME, value)
        .httpOnly(true)
        .secure(secureCookie)
        .sameSite("Strict")
        .path("/api/v1/auth")
        .build();
  }

  private ResponseCookie expiredRefreshCookie() {
    return ResponseCookie.from(REFRESH_COOKIE_NAME, "")
        .httpOnly(true)
        .secure(secureCookie)
        .sameSite("Strict")
        .path("/api/v1/auth")
        .maxAge(0)
        .build();
  }
}
