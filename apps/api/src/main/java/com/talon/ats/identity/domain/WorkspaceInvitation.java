package com.talon.ats.identity.domain;

import com.talon.ats.identity.contract.WorkspaceRole;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record WorkspaceInvitation(
    UUID id,
    UUID workspaceId,
    String email,
    WorkspaceRole role,
    String tokenHash,
    Instant expiresAt,
    Instant acceptedAt,
    UUID invitedBy,
    Instant createdAt) {

  public WorkspaceInvitation {
    Objects.requireNonNull(id);
    Objects.requireNonNull(workspaceId);
    email = normalizedEmail(email);
    Objects.requireNonNull(role);
    if (tokenHash == null || tokenHash.isBlank()) {
      throw new IllegalArgumentException("tokenHash is required");
    }
    tokenHash = tokenHash.trim();
    Objects.requireNonNull(expiresAt);
    Objects.requireNonNull(invitedBy);
    Objects.requireNonNull(createdAt);
    if (!expiresAt.isAfter(createdAt)) {
      throw new IllegalArgumentException("expiresAt must be after createdAt");
    }
  }

  public WorkspaceInvitation accept(String authenticatedEmail, Instant now) {
    Objects.requireNonNull(now, "acceptance time is required");
    if (acceptedAt != null) {
      throw new InvitationNotAcceptableException("Invitation was already accepted");
    }
    if (!now.isBefore(expiresAt)) {
      throw new InvitationNotAcceptableException("Invitation has expired");
    }
    if (!email.equals(normalizedEmail(authenticatedEmail))) {
      throw new InvitationNotAcceptableException(
          "Authenticated email does not match the invitation email");
    }
    return new WorkspaceInvitation(
        id, workspaceId, email, role, tokenHash, expiresAt, now, invitedBy, createdAt);
  }

  private static String normalizedEmail(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("email is required");
    }
    return value.trim().toLowerCase(Locale.ROOT);
  }
}
