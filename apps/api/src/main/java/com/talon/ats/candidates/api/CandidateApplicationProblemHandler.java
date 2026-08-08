package com.talon.ats.candidates.api;

import com.talon.ats.candidates.application.CandidateQueryException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = CandidateApplicationController.class)
public class CandidateApplicationProblemHandler {

  @ExceptionHandler(CandidateQueryException.class)
  ProblemDetail candidateQuery(CandidateQueryException exception) {
    HttpStatus status =
        switch (exception.code()) {
          case "CANDIDATE_FORBIDDEN" -> HttpStatus.FORBIDDEN;
          case "CANDIDATE_APPLICATION_NOT_FOUND" -> HttpStatus.NOT_FOUND;
          case "RESUME_NOT_CLEAN" -> HttpStatus.CONFLICT;
          case "CANDIDATE_CURSOR_INVALID", "CANDIDATE_PAGE_INVALID" -> HttpStatus.BAD_REQUEST;
          default -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
    problem.setTitle("Candidate request failed");
    problem.setProperty("code", exception.code());
    return problem;
  }
}
