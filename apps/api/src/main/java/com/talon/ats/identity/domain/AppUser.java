package com.talon.ats.identity.domain;

import java.time.Instant;
import java.util.UUID;

public record AppUser(
    UUID id,
    String email,
    String normalizedEmail,
    String displayName,
    String passwordHash,
    AppUserStatus status,
    Instant createdAt,
    Instant lastLoginAt) {}
