package com.talon.ats.identity.api;

import com.talon.ats.identity.application.AuthenticationFailedException;
import com.talon.ats.identity.application.RefreshSessionRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthenticationExceptionHandler {

  @ExceptionHandler(AuthenticationFailedException.class)
  ProblemDetail invalidCredentials(AuthenticationFailedException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage());
    problem.setTitle("Authentication failed");
    problem.setProperty("code", "INVALID_CREDENTIALS");
    return problem;
  }

  @ExceptionHandler(RefreshSessionRejectedException.class)
  ProblemDetail sessionExpired(RefreshSessionRejectedException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage());
    problem.setTitle("Session expired");
    problem.setProperty("code", "SESSION_EXPIRED");
    return problem;
  }
}
