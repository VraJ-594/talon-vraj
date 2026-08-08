import type { CandidateApplicationSummary } from '../candidates/candidateGateway';

export type SearchField =
  | 'LOCATION'
  | 'CURRENT_TITLE'
  | 'CURRENT_COMPANY'
  | 'SKILLS'
  | 'APPLICATION_STAGE'
  | 'JOB_TITLE'
  | 'SOURCE'
  | 'EXPERIENCE_MONTHS'
  | 'NOTICE_PERIOD_DAYS'
  | 'APPLIED_AT'
  | 'AVAILABLE_FROM'
  | 'CURRENT_COMPENSATION'
  | 'EXPECTED_COMPENSATION';

export type SearchOperator =
  | 'EQUALS'
  | 'CONTAINS'
  | 'GREATER_THAN'
  | 'GREATER_THAN_OR_EQUAL'
  | 'LESS_THAN'
  | 'LESS_THAN_OR_EQUAL'
  | 'BEFORE'
  | 'AFTER'
  | 'ON';

export type SearchPredicate = {
  readonly field: SearchField;
  readonly operator: SearchOperator;
  readonly value: string;
  readonly currency: string | null;
};

export type CandidateSearchCriteria = {
  readonly dslVersion: '1';
  readonly text: string | null;
  readonly predicates: readonly SearchPredicate[];
  readonly sort: {
    readonly field: 'APPLIED_AT' | 'EXPERIENCE';
    readonly direction: 'ASC' | 'DESC';
  };
  readonly limit: number;
  readonly cursor: string | null;
};

export type SearchFilterChip = {
  readonly field: SearchField;
  readonly operator: SearchOperator;
  readonly label: string;
  readonly value: string;
};

export type InterpretedCandidateSearch = {
  readonly criteria: CandidateSearchCriteria;
  readonly chips: readonly SearchFilterChip[];
  readonly warnings: readonly string[];
};

export type CandidateSearchPage = {
  readonly results: readonly CandidateApplicationSummary[];
  readonly nextCursor: string | null;
};

export type CommandSearchItem = {
  readonly type: 'CANDIDATE' | 'JOB';
  readonly id: string;
  readonly applicationId: string | null;
  readonly label: string;
  readonly description: string;
};

export type SearchProblemCode =
  | 'INTERPRETER_DISABLED'
  | 'INTERPRETER_UNAVAILABLE'
  | 'INTERPRETER_QUOTA_EXCEEDED'
  | 'INTERPRETATION_INVALID'
  | 'INTERPRETATION_RATE_LIMITED'
  | 'AMBIGUOUS_CURRENCY'
  | 'SEARCH_INVALID';

export class SearchGatewayError extends Error {
  constructor(readonly code: SearchProblemCode) {
    super('Candidate search could not be completed');
    this.name = 'SearchGatewayError';
  }
}

export interface SearchGateway {
  command(query: string): Promise<readonly CommandSearchItem[]>;
  interpret(input: {
    readonly query: string;
    readonly locale: string;
    readonly timezone: string;
  }): Promise<InterpretedCandidateSearch>;
  query(criteria: CandidateSearchCriteria): Promise<CandidateSearchPage>;
}
