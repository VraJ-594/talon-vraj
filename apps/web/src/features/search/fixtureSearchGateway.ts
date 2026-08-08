import { createFixtureCandidateGateway } from '../candidates/fixtureCandidateGateway';
import type { CandidateApplicationSummary } from '../candidates/candidateGateway';
import type { CandidateSearchCriteria, SearchGateway, SearchPredicate } from './searchGateway';

function matchesPredicate(candidate: CandidateApplicationSummary, predicate: SearchPredicate) {
  if (predicate.field === 'LOCATION') {
    return candidate.location.toLowerCase().includes(predicate.value.toLowerCase());
  }
  if (predicate.field === 'SKILLS') {
    return candidate.skills.some((skill) =>
      skill.toLowerCase().includes(predicate.value.toLowerCase()),
    );
  }
  if (predicate.field === 'EXPECTED_COMPENSATION') {
    const amount = candidate.expectedCompensation?.minorUnits;
    if (amount === undefined || candidate.expectedCompensation?.currency !== predicate.currency) {
      return false;
    }
    const expected = Number(predicate.value);
    if (predicate.operator === 'LESS_THAN') return amount < expected;
    if (predicate.operator === 'LESS_THAN_OR_EQUAL') return amount <= expected;
    if (predicate.operator === 'GREATER_THAN') return amount > expected;
    return amount >= expected;
  }
  return true;
}

export function createFixtureSearchGateway(): SearchGateway {
  const candidates = createFixtureCandidateGateway();
  return {
    async command(query) {
      const normalized = query.toLowerCase();
      return (await candidates.listApplications()).items
        .filter((candidate) => candidate.candidateName.toLowerCase().includes(normalized))
        .map((candidate) => ({
          type: 'CANDIDATE' as const,
          id: candidate.candidateId,
          applicationId: candidate.applicationId,
          label: candidate.candidateName,
          description: `${candidate.currentTitle} · ${candidate.currentCompany}`,
        }));
    },
    async interpret({ query }) {
      const compensation = query.match(/(?:below|under|less than|<)\s*(\d+(?:\.\d+)?)\s*lpa/i);
      const predicates: SearchPredicate[] = compensation
        ? [
            {
              field: 'EXPECTED_COMPENSATION',
              operator: 'LESS_THAN',
              value: String(Math.round(Number(compensation[1]) * 10_000_000)),
              currency: 'INR',
            },
          ]
        : [];
      const criteria: CandidateSearchCriteria = {
        dslVersion: '1',
        text: predicates.length ? null : query,
        predicates,
        sort: { field: 'APPLIED_AT', direction: 'DESC' },
        limit: 25,
        cursor: null,
      };
      return {
        criteria,
        chips: predicates.map((predicate) => ({
          field: predicate.field,
          operator: predicate.operator,
          label: 'Expected compensation less than',
          value: `${Number(predicate.value) / 10_000_000} LPA`,
        })),
        warnings: predicates.length
          ? ['LPA was interpreted as annual INR; no currency conversion was applied.']
          : [],
      };
    },
    async query(criteria) {
      const all = (await candidates.listApplications()).items;
      const text = criteria.text?.toLowerCase();
      return {
        results: all.filter(
          (candidate) =>
            (!text ||
              `${candidate.candidateName} ${candidate.skills.join(' ')}`
                .toLowerCase()
                .includes(text)) &&
            criteria.predicates.every((predicate) => matchesPredicate(candidate, predicate)),
        ),
        nextCursor: null,
      };
    },
  };
}
