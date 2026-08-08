package com.talon.ats.search.application;

import java.util.UUID;

public record CommandSearchItem(
    String type, UUID id, UUID applicationId, String label, String description) {}
