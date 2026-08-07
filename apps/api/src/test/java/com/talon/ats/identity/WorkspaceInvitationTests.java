package com.talon.ats.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.talon.ats.identity.contract.WorkspaceRole;
import com.talon.ats.identity.domain.InvitationNotAcceptableException;
import com.talon.ats.identity.domain.WorkspaceInvitation;
import com.talon.ats.identity.domain.WorkspacePermissionPolicy;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class WorkspaceInvitationTests {

  private static final Instant NOW = Instant.parse("2026-08-07T10:00:00Z");

  @Test
  void acceptsMatchingEmailOnlyOnceBeforeExpiry() {
    WorkspaceInvitation invitation = invitation(NOW.plusSeconds(3600));

    WorkspaceInvitation accepted = invitation.accept(" Candidate@Example.com ", NOW);

    assertThat(accepted.acceptedAt()).isEqualTo(NOW);
    assertThatThrownBy(() -> accepted.accept("candidate@example.com", NOW.plusSeconds(1)))
        .isInstanceOf(InvitationNotAcceptableException.class)
        .hasMessageContaining("already accepted");
  }

  @Test
  void rejectsInvitationAtOrAfterExpiry() {
    WorkspaceInvitation invitation = invitation(NOW);

    assertThatThrownBy(() -> invitation.accept("candidate@example.com", NOW))
        .isInstanceOf(InvitationNotAcceptableException.class)
        .hasMessageContaining("expired");
  }

  @Test
  void rejectsIdentityWhoseEmailDoesNotMatchInvitation() {
    WorkspaceInvitation invitation = invitation(NOW.plusSeconds(3600));

    assertThatThrownBy(() -> invitation.accept("someone@example.com", NOW))
        .isInstanceOf(InvitationNotAcceptableException.class)
        .hasMessageContaining("email");
  }

  @Test
  void permitsOnlyWorkspaceAdministratorsToManageMembers() {
    assertThat(WorkspacePermissionPolicy.canManageMembers(WorkspaceRole.WORKSPACE_ADMIN)).isTrue();
  }

  @ParameterizedTest
  @MethodSource("nonAdministratorRoles")
  void deniesMemberManagementToNonAdministratorRoles(WorkspaceRole role) {
    assertThat(WorkspacePermissionPolicy.canManageMembers(role)).isFalse();
  }

  private static Stream<WorkspaceRole> nonAdministratorRoles() {
    return Stream.of(
        WorkspaceRole.RECRUITER, WorkspaceRole.HIRING_MANAGER, WorkspaceRole.INTERVIEWER);
  }

  private static WorkspaceInvitation invitation(Instant expiresAt) {
    return new WorkspaceInvitation(
        uuid(1),
        uuid(2),
        "candidate@example.com",
        WorkspaceRole.RECRUITER,
        "sha256:token-hash",
        expiresAt,
        null,
        uuid(3),
        NOW.minusSeconds(60));
  }

  private static UUID uuid(long value) {
    return new UUID(0, value);
  }
}
