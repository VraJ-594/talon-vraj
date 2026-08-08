package com.talon.ats.search.domain;

import java.util.UUID;

public record SearchCursor(String sortValue, UUID applicationId) {}
