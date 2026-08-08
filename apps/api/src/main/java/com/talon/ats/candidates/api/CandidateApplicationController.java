package com.talon.ats.candidates.api;

import com.talon.ats.candidates.application.AnnualCompensation;
import com.talon.ats.candidates.application.CandidateApplicationDetail;
import com.talon.ats.candidates.application.CandidateApplicationPage;
import com.talon.ats.candidates.application.CandidateApplicationQueryService;
import com.talon.ats.candidates.application.CandidateApplicationSummary;
import com.talon.ats.candidates.application.CandidateResume;
import com.talon.ats.candidates.application.CandidateResumeStatus;
import com.talon.ats.files.application.ObjectStorage;
import com.talon.ats.identity.contract.WorkspaceRole;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "talon.security.enabled", havingValue = "true")
public class CandidateApplicationController {

  private static final Duration DOWNLOAD_LIFETIME = Duration.ofMinutes(5);

  private final CandidateApplicationQueryService service;
  private final ObjectStorage storage;

  public CandidateApplicationController(
      CandidateApplicationQueryService service, ObjectStorage storage) {
    this.service = service;
    this.storage = storage;
  }

  @GetMapping("/api/v1/applications")
  CandidateApplicationPage list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "50") int limit) {
    return service.list(actor(jwt), cursor, limit);
  }

  @GetMapping("/api/v1/applications/{applicationId}")
  ApplicationDetailResponse detail(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID applicationId) {
    return ApplicationDetailResponse.from(service.detail(actor(jwt), applicationId));
  }

  @GetMapping("/api/v1/applications/{applicationId}/resume-download")
  ResponseEntity<?> download(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID applicationId) {
    CandidateResume resume = service.resume(actor(jwt), applicationId);
    URI presigned =
        storage
            .presignCleanDownload(resume.objectKey(), DOWNLOAD_LIFETIME, resume.fileName())
            .orElse(null);
    if (presigned != null) {
      return ResponseEntity.status(303).location(presigned).build();
    }
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(resume.contentType()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(resume.fileName()).build().toString())
        .body(new InputStreamResource(storage.open(resume.objectKey())));
  }

  private static CandidateApplicationQueryService.Actor actor(Jwt jwt) {
    return new CandidateApplicationQueryService.Actor(
        UUID.fromString(jwt.getSubject()),
        UUID.fromString(jwt.getClaimAsString("workspace_id")),
        WorkspaceRole.valueOf(jwt.getClaimAsString("role")));
  }

  record AdditionalAnswerResponse(String question, String answer) {}

  record ApplicationDetailResponse(
      UUID applicationId,
      UUID candidateId,
      String candidateName,
      String jobTitle,
      String stage,
      String location,
      int totalExperienceMonths,
      String currentCompany,
      String currentTitle,
      List<String> skills,
      AnnualCompensation currentCompensation,
      AnnualCompensation expectedCompensation,
      int noticePeriodDays,
      LocalDate applicationDate,
      CandidateResumeStatus resumeStatus,
      String email,
      String maskedPhone,
      String source,
      LocalDate availableFrom,
      List<AdditionalAnswerResponse> additionalAnswers,
      String resumeFileName,
      boolean resumeDownloadAllowed) {

    static ApplicationDetailResponse from(CandidateApplicationDetail detail) {
      CandidateApplicationSummary summary = detail.summary();
      List<AdditionalAnswerResponse> answers =
          detail.additionalAnswers().entrySet().stream()
              .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
              .map(entry -> new AdditionalAnswerResponse(entry.getKey(), entry.getValue()))
              .toList();
      return new ApplicationDetailResponse(
          summary.applicationId(),
          summary.candidateId(),
          summary.candidateName(),
          summary.jobTitle(),
          summary.stage(),
          summary.location(),
          summary.totalExperienceMonths(),
          summary.currentCompany(),
          summary.currentTitle(),
          summary.skills(),
          summary.currentCompensation(),
          summary.expectedCompensation(),
          summary.noticePeriodDays(),
          summary.applicationDate(),
          summary.resumeStatus(),
          detail.email(),
          detail.maskedPhone(),
          detail.source(),
          detail.availableFrom(),
          answers,
          detail.resumeFileName(),
          detail.resumeDownloadAllowed());
    }
  }
}
