package com.talon.ats.identity;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.talon.ats.identity.api.AuthController;
import com.talon.ats.identity.api.AuthenticationExceptionHandler;
import com.talon.ats.identity.application.AuthenticationFailedException;
import com.talon.ats.identity.application.AuthenticationResult;
import com.talon.ats.identity.application.AuthenticationService;
import com.talon.ats.identity.application.RefreshSessionRejectedException;
import com.talon.ats.identity.infrastructure.security.SecurityConfiguration;
import com.talon.ats.platform.health.HealthController;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = {AuthController.class, HealthController.class},
    properties = "talon.security.enabled=true")
@Import({SecurityConfiguration.class, AuthenticationExceptionHandler.class})
class AuthControllerTests {

  private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AuthenticationService authenticationService;

  @MockitoBean private JwtDecoder jwtDecoder;

  @Test
  void healthAndLoginArePublicAndLoginUsesSecureRefreshCookie() throws Exception {
    given(authenticationService.authenticate(any()))
        .willReturn(
            new AuthenticationResult(
                USER_ID,
                WORKSPACE_ID,
                "Talon Demo",
                com.talon.ats.identity.contract.WorkspaceRole.WORKSPACE_ADMIN,
                "Vraj",
                "access-token",
                Instant.parse("2026-08-07T10:15:00Z"),
                "raw-refresh-token",
                Instant.parse("2026-08-14T10:00:00Z")));

    mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"vraj@example.com","password":"correct-password"}
                    """))
        .andExpect(status().isOk())
        .andExpect(header().string("Set-Cookie", containsString("talon_refresh=raw-refresh-token")))
        .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
        .andExpect(header().string("Set-Cookie", containsString("Secure")))
        .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
        .andExpect(header().string("Set-Cookie", not(containsString("Max-Age"))))
        .andExpect(header().string("Set-Cookie", not(containsString("Expires"))))
        .andExpect(jsonPath("$.accessToken").value("access-token"))
        .andExpect(jsonPath("$.refreshToken").doesNotExist())
        .andExpect(jsonPath("$.workspaceId").value(WORKSPACE_ID.toString()))
        .andExpect(jsonPath("$.workspaceName").value("Talon Demo"))
        .andExpect(jsonPath("$.role").value("WORKSPACE_ADMIN"));
  }

  @Test
  void refreshIsPublicRotatesTheSessionCookieAndReturnsANewAccessToken() throws Exception {
    given(authenticationService.refresh("current-refresh-token"))
        .willReturn(authenticationResult("rotated-access-token", "rotated-refresh-token"));

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .cookie(new Cookie("talon_refresh", "current-refresh-token")))
        .andExpect(status().isOk())
        .andExpect(
            header().string("Set-Cookie", containsString("talon_refresh=rotated-refresh-token")))
        .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
        .andExpect(header().string("Set-Cookie", containsString("Secure")))
        .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
        .andExpect(header().string("Set-Cookie", not(containsString("Max-Age"))))
        .andExpect(jsonPath("$.accessToken").value("rotated-access-token"))
        .andExpect(jsonPath("$.refreshToken").doesNotExist());
  }

  @Test
  void missingRefreshCookieReturnsSafeSessionExpiredProblem() throws Exception {
    given(authenticationService.refresh(null)).willThrow(new RefreshSessionRejectedException());

    mockMvc
        .perform(post("/api/v1/auth/refresh"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"))
        .andExpect(jsonPath("$.detail").value("Session is unavailable"));
  }

  @Test
  void logoutIsPublicRevokesWhenPresentAndAlwaysClearsTheCookie() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .cookie(new Cookie("talon_refresh", "current-refresh-token")))
        .andExpect(status().isNoContent())
        .andExpect(header().string("Set-Cookie", containsString("talon_refresh=")))
        .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")))
        .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
        .andExpect(header().string("Set-Cookie", containsString("Secure")))
        .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")));

    verify(authenticationService).logout("current-refresh-token");
  }

  @Test
  void protectedSessionRejectsAnonymousRequests() throws Exception {
    mockMvc.perform(get("/api/v1/session")).andExpect(status().isUnauthorized());
  }

  @Test
  void sessionReturnsOnlyVerifiedJwtIdentityClaims() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/session")
                .with(
                    jwt()
                        .jwt(
                            token ->
                                token
                                    .subject(USER_ID.toString())
                                    .claim("workspace_id", WORKSPACE_ID.toString())
                                    .claim("workspace_name", "Talon Demo")
                                    .claim("role", "WORKSPACE_ADMIN")
                                    .claim("display_name", "Vraj"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
        .andExpect(jsonPath("$.workspaceId").value(WORKSPACE_ID.toString()))
        .andExpect(jsonPath("$.workspaceName").value("Talon Demo"))
        .andExpect(jsonPath("$.role").value("WORKSPACE_ADMIN"))
        .andExpect(jsonPath("$.displayName").value("Vraj"));
  }

  @Test
  void invalidCredentialsReturnGenericProblemWithoutAccountDisclosure() throws Exception {
    given(authenticationService.authenticate(any())).willThrow(new AuthenticationFailedException());

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"missing@example.com","password":"guess"}
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
        .andExpect(jsonPath("$.detail").value("Invalid email or password"));
  }

  private static AuthenticationResult authenticationResult(
      String accessToken, String refreshToken) {
    return new AuthenticationResult(
        USER_ID,
        WORKSPACE_ID,
        "Talon Demo",
        com.talon.ats.identity.contract.WorkspaceRole.WORKSPACE_ADMIN,
        "Vraj",
        accessToken,
        Instant.parse("2026-08-07T10:15:00Z"),
        refreshToken,
        Instant.parse("2026-08-14T10:00:00Z"));
  }
}
