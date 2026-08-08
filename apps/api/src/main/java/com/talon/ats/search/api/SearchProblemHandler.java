package com.talon.ats.search.api;

import com.talon.ats.search.application.InterpretationRateLimitException;
import com.talon.ats.search.application.SearchInterpreterException;
import com.talon.ats.search.application.SearchValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = SearchController.class)
public class SearchProblemHandler {

  @ExceptionHandler(SearchValidationException.class)
  ProblemDetail validation(SearchValidationException exception) {
    return problem(HttpStatus.BAD_REQUEST, exception.code(), exception.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail requestValidation(MethodArgumentNotValidException exception) {
    return problem(
        HttpStatus.BAD_REQUEST,
        "NATURAL_QUERY_INVALID",
        "Natural-language query must contain 1 to 500 characters");
  }

  @ExceptionHandler(InterpretationRateLimitException.class)
  ProblemDetail rateLimited(InterpretationRateLimitException exception) {
    return problem(
        HttpStatus.TOO_MANY_REQUESTS, "INTERPRETATION_RATE_LIMITED", exception.getMessage());
  }

  @ExceptionHandler(SearchInterpreterException.class)
  ProblemDetail interpreter(SearchInterpreterException exception) {
    HttpStatus status =
        "INTERPRETATION_INVALID".equals(exception.code())
            ? HttpStatus.UNPROCESSABLE_ENTITY
            : HttpStatus.SERVICE_UNAVAILABLE;
    return problem(status, exception.code(), exception.getMessage());
  }

  private static ProblemDetail problem(HttpStatus status, String code, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle("Candidate search failed");
    problem.setProperty("code", code);
    return problem;
  }
}
