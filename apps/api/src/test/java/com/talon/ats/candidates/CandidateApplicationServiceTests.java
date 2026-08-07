package com.talon.ats.candidates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.talon.ats.candidates.application.AnnualCompensation;
import com.talon.ats.candidates.application.ApplicationData;
import com.talon.ats.candidates.application.CandidateApplicationCommand;
import com.talon.ats.candidates.application.CandidateApplicationResult;
import com.talon.ats.candidates.application.CandidateApplicationService;
import com.talon.ats.candidates.application.CandidateApplicationStore;
import com.talon.ats.candidates.application.CandidateData;
import com.talon.ats.identity.contract.WorkspaceRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CandidateApplicationServiceTests {

  private static final UUID USER_ID = new UUID(0, 1);
  private static final UUID WORKSPACE_ID = new UUID(0, 2);
  private static final UUID JOB_ID = new UUID(0, 3);
  private static final UUID CANDIDATE_ID = new UUID(0, 4);
  private static final UUID APPLICATION_ID = new UUID(0, 5);
  private static final Instant NOW = Instant.parse("2026-08-07T18:30:00Z");

  @Test
  void normalizesAndPersistsTypedApplicationDataInsideTheActorWorkspace() {
    RecordingStore store = new RecordingStore(true);

    CandidateApplicationResult result =
        service(store)
            .createOrMatch(
                actor(),
                new CandidateApplicationCommand(
                    JOB_ID,
                    new CandidateData(
                        " Nila@Example.com ",
                        " Nila ",
                        " Raman ",
                        "+91 99999 99999",
                        " Pune ",
                        " Engineer ",
                        " Example Ltd ",
                        "Java, PostgreSQL",
                        72),
                    new ApplicationData(
                        "GOOGLE_FORM",
                        LocalDate.parse("2026-08-07"),
                        30,
                        LocalDate.parse("2026-09-06"),
                        new AnnualCompensation("INR", 300_000_000L),
                        new AnnualCompensation("INR", 400_000_000L),
                        Map.of("preferredTeam", "Platform"))));

    assertThat(result).isEqualTo(store.result);
    assertThat(store.workspaceId).isEqualTo(WORKSPACE_ID);
    assertThat(store.candidateId).isEqualTo(CANDIDATE_ID);
    assertThat(store.applicationId).isEqualTo(APPLICATION_ID);
    assertThat(store.command.candidate().email()).isEqualTo("nila@example.com");
    assertThat(store.command.candidate().firstName()).isEqualTo("Nila");
    assertThat(store.command.candidate().location()).isEqualTo("Pune");
    assertThat(store.recordedAt).isEqualTo(NOW);
  }

  @Test
  void rejectsAJobThatIsNotAnActiveImportTargetInTheActorWorkspace() {
    RecordingStore store = new RecordingStore(false);

    assertThatThrownBy(() -> service(store).createOrMatch(actor(), validCommand()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("job is not an active import target");
    assertThat(store.command).isNull();
  }

  @Test
  void rejectsBlankCandidateEmailBeforeCallingPersistence() {
    RecordingStore store = new RecordingStore(true);

    assertThatThrownBy(
            () ->
                service(store)
                    .createOrMatch(
                        actor(),
                        new CandidateApplicationCommand(
                            JOB_ID,
                            new CandidateData(
                                " ", "Nila", "Raman", null, null, null, null, null, null),
                            validCommand().application())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("email is required");
    assertThat(store.command).isNull();
  }

  private static CandidateApplicationService service(CandidateApplicationStore store) {
    java.util.ArrayDeque<UUID> ids = new java.util.ArrayDeque<>();
    ids.add(CANDIDATE_ID);
    ids.add(APPLICATION_ID);
    return new CandidateApplicationService(
        store, ids::removeFirst, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static CandidateApplicationService.Actor actor() {
    return new CandidateApplicationService.Actor(USER_ID, WORKSPACE_ID, WorkspaceRole.RECRUITER);
  }

  private static CandidateApplicationCommand validCommand() {
    return new CandidateApplicationCommand(
        JOB_ID,
        new CandidateData("nila@example.com", "Nila", "Raman", null, null, null, null, null, null),
        new ApplicationData(
            "GOOGLE_FORM", LocalDate.parse("2026-08-07"), null, null, null, null, Map.of()));
  }

  private static final class RecordingStore implements CandidateApplicationStore {
    private final boolean activeJob;
    private final CandidateApplicationResult result =
        new CandidateApplicationResult(CANDIDATE_ID, APPLICATION_ID, true, true);
    private UUID workspaceId;
    private UUID candidateId;
    private UUID applicationId;
    private CandidateApplicationCommand command;
    private Instant recordedAt;

    private RecordingStore(boolean activeJob) {
      this.activeJob = activeJob;
    }

    @Override
    public boolean isActiveImportTarget(UUID workspaceId, UUID jobId) {
      this.workspaceId = workspaceId;
      return activeJob;
    }

    @Override
    public CandidateApplicationResult saveOrMatch(
        UUID workspaceId,
        UUID candidateId,
        UUID applicationId,
        CandidateApplicationCommand command,
        Instant recordedAt) {
      this.workspaceId = workspaceId;
      this.candidateId = candidateId;
      this.applicationId = applicationId;
      this.command = command;
      this.recordedAt = recordedAt;
      return result;
    }
  }
}
