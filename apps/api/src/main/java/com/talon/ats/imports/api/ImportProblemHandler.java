package com.talon.ats.imports.api;

import com.talon.ats.imports.application.CsvParseException;
import com.talon.ats.imports.application.ImportProblem;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = ImportController.class)
public class ImportProblemHandler {

  @ExceptionHandler(CsvParseException.class)
  ProblemDetail csvProblem(CsvParseException exception) {
    return problem(status(exception.code()), exception.code(), exception.getMessage());
  }

  @ExceptionHandler(ImportProblem.class)
  ProblemDetail importProblem(ImportProblem exception) {
    return problem(status(exception.code()), exception.code(), exception.getMessage());
  }

  @ExceptionHandler(SecurityException.class)
  ProblemDetail forbidden(SecurityException exception) {
    return problem(HttpStatus.FORBIDDEN, "IMPORT_FORBIDDEN", "Recruiting access is required");
  }

  private static ProblemDetail problem(HttpStatus status, String code, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle("Candidate import failed");
    problem.setProperty("code", code);
    return problem;
  }

  private static HttpStatus status(String code) {
    return switch (code) {
      case "FILE_TOO_LARGE" -> HttpStatus.PAYLOAD_TOO_LARGE;
      case "IMPORT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
      case "JOB_NOT_IMPORTABLE" -> HttpStatus.CONFLICT;
      case "IMPORT_STORAGE_FAILED" -> HttpStatus.SERVICE_UNAVAILABLE;
      default -> HttpStatus.BAD_REQUEST;
    };
  }
}
