package com.talon.ats.identity.domain;

import java.time.Instant;
import java.util.UUID;

public record Workspace(
    UUID id,
    String name,
    String slug,
    String defaultTimezone,
    int retentionMonths,
    Instant createdAt,
    UUID createdBy,
    long version) {}
