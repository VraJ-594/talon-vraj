package com.talon.ats.search.application;

import com.talon.ats.search.domain.SearchField;
import com.talon.ats.search.domain.SearchOperator;

public record SearchFilterChip(
    SearchField field, SearchOperator operator, String label, String value) {}
