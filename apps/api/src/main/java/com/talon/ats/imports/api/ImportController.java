package com.talon.ats.imports.api;

import com.talon.ats.identity.contract.WorkspaceRole;
import com.talon.ats.imports.application.CsvPreviewIssue;
import com.talon.ats.imports.application.ImportDraft;
import com.talon.ats.imports.application.ImportDraftService;
import com.talon.ats.imports.application.ImportPreviewSnapshot;
import com.talon.ats.imports.application.ImportProblem;
import com.talon.ats.imports.application.ImportProgressSnapshot;
import com.talon.ats.imports.domain.CanonicalField;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/imports")
@ConditionalOnProperty(name = "talon.security.enabled", havingValue = "true")
public class ImportController {

  private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

  private final ImportDraftService service;

  public ImportController(ImportDraftService service) {
    this.service = service;
  }

  @GetMapping("/template")
  ResponseEntity<byte[]> template(@AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok()
        .contentType(CSV)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename("talon-candidate-import.csv")
                .build()
                .toString())
        .body(service.template(actor(jwt)));
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  ImportDraftResponse upload(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam UUID jobId,
      @RequestPart("file") MultipartFile file) {
    ImportDraft draft =
        service.upload(actor(jwt), jobId, file.getOriginalFilename(), file::getInputStream);
    return ImportDraftResponse.from(draft);
  }

  @PostMapping(path = "/{importId}/validate", consumes = MediaType.APPLICATION_JSON_VALUE)
  ImportPreviewResponse validate(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID importId,
      @RequestBody ValidateImportRequest request) {
    return ImportPreviewResponse.from(
        service.validate(
            actor(jwt), importId, mapping(request.mapping()), request.retainUnmapped()));
  }

  @GetMapping("/{importId}/preview")
  ImportPreviewResponse preview(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID importId) {
    return ImportPreviewResponse.from(service.preview(actor(jwt), importId));
  }

  @PostMapping("/{importId}/confirm")
  ImportProgressResponse confirm(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID importId,
      @RequestHeader("Idempotency-Key") UUID idempotencyKey) {
    return ImportProgressResponse.from(service.confirm(actor(jwt), importId, idempotencyKey));
  }

  @GetMapping("/{importId}")
  ImportProgressResponse progress(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID importId) {
    return ImportProgressResponse.from(service.progress(actor(jwt), importId));
  }

  private static ImportDraftService.Actor actor(Jwt jwt) {
    return new ImportDraftService.Actor(
        UUID.fromString(jwt.getSubject()),
        UUID.fromString(jwt.getClaimAsString("workspace_id")),
        WorkspaceRole.valueOf(jwt.getClaimAsString("role")));
  }

  private static Map<String, CanonicalField> mapping(Map<String, String> requested) {
    if (requested == null) {
      throw new ImportProblem("MISSING_REQUIRED_MAPPING", "The recognized mapping is required");
    }
    Map<String, CanonicalField> result = new LinkedHashMap<>();
    requested.forEach(
        (source, target) -> {
          try {
            result.put(source, CanonicalField.valueOf(target.toUpperCase(Locale.ROOT)));
          } catch (NullPointerException | IllegalArgumentException exception) {
            throw new ImportProblem(
                "UNSUPPORTED_SOURCE_COLUMN", "The mapping contains an unsupported Talon field");
          }
        });
    return result;
  }

  record ValidateImportRequest(Map<String, String> mapping, boolean retainUnmapped) {}

  record ImportDraftResponse(
      UUID id,
      UUID jobId,
      String fileName,
      int rowCount,
      List<String> sourceColumns,
      Map<String, String> suggestedMapping,
      String status) {

    static ImportDraftResponse from(ImportDraft draft) {
      Map<String, String> mapping = new LinkedHashMap<>();
      draft
          .suggestedMapping()
          .forEach((source, target) -> mapping.put(source, target.name().toLowerCase(Locale.ROOT)));
      return new ImportDraftResponse(
          draft.id(),
          draft.jobId(),
          draft.fileName(),
          draft.rowCount(),
          draft.sourceColumns(),
          Map.copyOf(mapping),
          draft.status().name());
    }
  }

  record ImportPreviewResponse(
      int validCount,
      int invalidCount,
      int duplicateCount,
      List<ImportPreviewIssueResponse> issues) {

    static ImportPreviewResponse from(ImportPreviewSnapshot preview) {
      return new ImportPreviewResponse(
          preview.validCount(),
          preview.invalidCount(),
          preview.duplicateCount(),
          preview.issues().stream().map(ImportPreviewIssueResponse::from).toList());
    }
  }

  record ImportPreviewIssueResponse(int rowNumber, String kind, String code, String message) {

    static ImportPreviewIssueResponse from(CsvPreviewIssue issue) {
      return new ImportPreviewIssueResponse(
          issue.rowNumber(), issue.kind(), issue.code(), issue.message());
    }
  }

  record ImportProgressResponse(
      UUID importId,
      String status,
      int processedCount,
      int totalCount,
      boolean errorCsvAvailable,
      List<ImportProgressRowResponse> rows) {

    static ImportProgressResponse from(ImportProgressSnapshot progress) {
      return new ImportProgressResponse(
          progress.importId(),
          progress.status().name(),
          progress.processedCount(),
          progress.totalCount(),
          progress.errorCsvAvailable(),
          progress.rows().stream().map(ImportProgressRowResponse::from).toList());
    }
  }

  record ImportProgressRowResponse(
      int rowNumber, String status, boolean retryable, String message) {

    static ImportProgressRowResponse from(ImportProgressSnapshot.Row row) {
      return new ImportProgressRowResponse(
          row.rowNumber(), row.status(), row.retryable(), row.message());
    }
  }
}
