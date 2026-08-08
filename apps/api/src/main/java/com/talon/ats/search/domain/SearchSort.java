package com.talon.ats.search.domain;

public record SearchSort(SearchSortField field, SortDirection direction) {

  public static SearchSort newestFirst() {
    return new SearchSort(SearchSortField.APPLIED_AT, SortDirection.DESC);
  }
}
