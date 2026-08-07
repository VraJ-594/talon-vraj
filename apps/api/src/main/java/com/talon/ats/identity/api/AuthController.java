package com.talon.ats.identity.api;

import com.talon.ats.identity.application.AuthenticateCommand;
import com.talon.ats.identity.application.AuthenticationResult;
import com.talon.ats.identity.application.AuthenticationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
  private static final Duration REFRESH_COOKIE_MAX_AGE = Duration.ofDays(7);

  private final AuthenticationService authenticationService;

  public AuthController(AuthenticationService authenticationService) {
    this.authenticationService = authenticationService;
  }

  @PostMapping("/auth/login")
  ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    AuthenticationResult result =
        authenticationService.authenticate(
            new AuthenticateCommand(request.email(), request.password()));
    ResponseCookie refreshCookie =
        ResponseCookie.from(REFRESH_COOKIE_NAME, result.refreshToken())
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/api/v1/auth")
            .maxAge(REFRESH_COOKIE_MAX_AGE)
            .build();

    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
        .body(LoginResponse.from(result));
  }

  @GetMapping("/session")
  SessionResponse session(@AuthenticationPrincipal Jwt jwt) {
    return new SessionResponse(
        UUID.fromString(jwt.getSubject()),
        UUID.fromString(jwt.getClaimAsString("workspace_id")),
        jwt.getClaimAsString("role"),
        jwt.getClaimAsString("display_name"));
  }

  record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

  record LoginResponse(
      UUID userId,
      UUID workspaceId,
      String role,
      String displayName,
      String accessToken,
      Instant accessTokenExpiresAt) {

    static LoginResponse from(AuthenticationResult result) {
      return new LoginResponse(
          result.userId(),
          result.workspaceId(),
          result.role().name(),
          result.displayName(),
          result.accessToken(),
          result.accessTokenExpiresAt());
    }
  }

  record SessionResponse(UUID userId, UUID workspaceId, String role, String displayName) {}
}
