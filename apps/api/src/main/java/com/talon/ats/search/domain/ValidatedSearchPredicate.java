package com.talon.ats.search.domain;

import java.time.LocalDate;

public record ValidatedSearchPredicate(
    SearchField field,
    SearchOperator operator,
    String textValue,
    Long numberValue,
    LocalDate dateValue,
    String currency) {}
