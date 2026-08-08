package com.talon.ats.candidates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.talon.ats.candidates.application.AnnualCompensation;
import com.talon.ats.candidates.application.CandidateApplicationCursor;
import com.talon.ats.candidates.application.CandidateApplicationDetail;
import com.talon.ats.candidates.application.CandidateApplicationPage;
import com.talon.ats.candidates.application.CandidateApplicationQueryService;
import com.talon.ats.candidates.application.CandidateApplicationQueryStore;
import com.talon.ats.candidates.application.CandidateApplicationSlice;
import com.talon.ats.candidates.application.CandidateApplicationSummary;
import com.talon.ats.candidates.application.CandidateQueryException;
import com.talon.ats.candidates.application.CandidateResume;
import com.talon.ats.candidates.application.CandidateResumeStatus;
import com.talon.ats.files.application.PrivateObjectKey;
import com.talon.ats.identity.contract.WorkspaceRole;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CandidateApplicationQueryServiceTests {

  private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID WORKSPACE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
  private static final UUID CANDIDATE_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
  private static final UUID APPLICATION_ID =
      UUID.fromString("40000000-0000-0000-0000-000000000001");
  private static final UUID FILE_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
  private static final UUID VERSION_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");

  @Test
  void returnsAnOpaquePageForTheVerifiedWorkspace() {
    CandidateApplicationSummary summary = summary();
    CandidateApplicationCursor next =
        new CandidateApplicationCursor(LocalDate.parse("2026-08-06"), APPLICATION_ID);
    RecordingStore store = new RecordingStore();
    store.slice = new CandidateApplicationSlice(List.of(summary), next);
    CandidateApplicationQueryService service = new CandidateApplicationQueryService(store);

    CandidateApplicationPage page = service.list(actor(WorkspaceRole.RECRUITER), null, 50);

    assertThat(page.items()).containsExactly(summary);
    assertThat(page.nextCursor()).isNotBlank().doesNotContain("2026-08-06");
    assertThat(store.workspaceId).isEqualTo(WORKSPACE_ID);
    assertThat(store.limit).isEqualTo(50);
  }

  @Test
  void decodesOnlyCursorsCreatedByTheServiceAndBoundsPageSize() {
    RecordingStore store = new RecordingStore();
    CandidateApplicationCursor next =
        new CandidateApplicationCursor(LocalDate.parse("2026-08-06"), APPLICATION_ID);
    store.slice = new CandidateApplicationSlice(List.of(summary()), next);
    CandidateApplicationQueryService service = new CandidateApplicationQueryService(store);
    String cursor = service.list(actor(WorkspaceRole.WORKSPACE_ADMIN), null, 100).nextCursor();

    service.list(actor(WorkspaceRole.WORKSPACE_ADMIN), cursor, 100);

    assertThat(store.cursor).isEqualTo(next);
    assertThatThrownBy(() -> service.list(actor(WorkspaceRole.RECRUITER), "not-a-cursor", 25))
        .isInstanceOf(CandidateQueryException.class)
        .extracting(error -> ((CandidateQueryException) error).code())
        .isEqualTo("CANDIDATE_CURSOR_INVALID");
    assertThatThrownBy(() -> service.list(actor(WorkspaceRole.RECRUITER), null, 101))
        .isInstanceOf(CandidateQueryException.class)
        .extracting(error -> ((CandidateQueryException) error).code())
        .isEqualTo("CANDIDATE_PAGE_INVALID");
  }

  @Test
  void permitsOnlyRecruitingRolesAndUsesSafeNotFoundFailures() {
    RecordingStore store = new RecordingStore();
    CandidateApplicationQueryService service = new CandidateApplicationQueryService(store);

    assertThatThrownBy(() -> service.list(actor(WorkspaceRole.INTERVIEWER), null, 25))
        .isInstanceOf(CandidateQueryException.class)
        .extracting(error -> ((CandidateQueryException) error).code())
        .isEqualTo("CANDIDATE_FORBIDDEN");
    assertThatThrownBy(() -> service.detail(actor(WorkspaceRole.RECRUITER), APPLICATION_ID))
        .isInstanceOf(CandidateQueryException.class)
        .extracting(error -> ((CandidateQueryException) error).code())
        .isEqualTo("CANDIDATE_APPLICATION_NOT_FOUND");
  }

  @Test
  void returnsDetailAndOnlyARealCleanResume() {
    RecordingStore store = new RecordingStore();
    CandidateApplicationDetail detail = detail();
    store.detail = Optional.of(detail);
    store.resume =
        Optional.of(
            new CandidateResume(
                WORKSPACE_ID,
                "synthetic-resume.pdf",
                "application/pdf",
                PrivateObjectKey.cleanResume(WORKSPACE_ID, FILE_ID, VERSION_ID)));
    CandidateApplicationQueryService service = new CandidateApplicationQueryService(store);

    assertThat(service.detail(actor(WorkspaceRole.WORKSPACE_ADMIN), APPLICATION_ID))
        .isEqualTo(detail);
    assertThat(service.resume(actor(WorkspaceRole.RECRUITER), APPLICATION_ID).fileName())
        .isEqualTo("synthetic-resume.pdf");

    store.resume = Optional.empty();
    assertThatThrownBy(() -> service.resume(actor(WorkspaceRole.RECRUITER), APPLICATION_ID))
        .isInstanceOf(CandidateQueryException.class)
        .extracting(error -> ((CandidateQueryException) error).code())
        .isEqualTo("RESUME_NOT_CLEAN");
  }

  private static CandidateApplicationQueryService.Actor actor(WorkspaceRole role) {
    return new CandidateApplicationQueryService.Actor(USER_ID, WORKSPACE_ID, role);
  }

  private static CandidateApplicationSummary summary() {
    return new CandidateApplicationSummary(
        APPLICATION_ID,
        CANDIDATE_ID,
        "Asha Mehta",
        "Senior Platform Engineer",
        "SCREENING",
        "Pune",
        96,
        "Finch Labs",
        "Senior Java Engineer",
        List.of("Java", "Spring Boot", "PostgreSQL"),
        new AnnualCompensation("INR", 320_000_000L),
        new AnnualCompensation("INR", 380_000_000L),
        30,
        LocalDate.parse("2026-08-06"),
        CandidateResumeStatus.NO_RESUME);
  }

  private static CandidateApplicationDetail detail() {
    return new CandidateApplicationDetail(
        summary(),
        "search-demo@example.test",
        "+91 ••••••1234",
        "SEARCH_DEMO",
        LocalDate.parse("2026-09-15"),
        Map.of("Preferred shift", "Day"),
        "",
        false);
  }

  private static final class RecordingStore implements CandidateApplicationQueryStore {
    private CandidateApplicationSlice slice = new CandidateApplicationSlice(List.of(), null);
    private Optional<CandidateApplicationDetail> detail = Optional.empty();
    private Optional<CandidateResume> resume = Optional.empty();
    private UUID workspaceId;
    private CandidateApplicationCursor cursor;
    private int limit;

    @Override
    public CandidateApplicationSlice list(
        UUID workspaceId, CandidateApplicationCursor cursor, int limit) {
      this.workspaceId = workspaceId;
      this.cursor = cursor;
      this.limit = limit;
      return slice;
    }

    @Override
    public Optional<CandidateApplicationDetail> findDetail(UUID workspaceId, UUID applicationId) {
      this.workspaceId = workspaceId;
      return detail;
    }

    @Override
    public Optional<CandidateResume> findCleanResume(UUID workspaceId, UUID applicationId) {
      this.workspaceId = workspaceId;
      return resume;
    }
  }
}
