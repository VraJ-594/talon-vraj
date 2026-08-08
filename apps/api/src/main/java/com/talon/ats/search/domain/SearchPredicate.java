package com.talon.ats.search.domain;

public record SearchPredicate(
    SearchField field, SearchOperator operator, String value, String currency) {}
