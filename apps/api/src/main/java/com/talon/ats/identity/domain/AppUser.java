package com.talon.ats.identity.domain;

import java.time.Instant;
import java.util.UUID;

public record AppUser(
    UUID id,
    String cognitoSubject,
    String email,
    String displayName,
    AppUserStatus status,
    Instant createdAt,
    Instant lastLoginAt) {}
