import { ArrowRight, Search, Sparkles, X } from 'lucide-react';
import { type FormEvent, useEffect, useMemo, useState } from 'react';

import type { CandidateApplicationSummary } from '../candidates/candidateGateway';
import {
  SearchGatewayError,
  type CandidateSearchCriteria,
  type SearchFilterChip,
  type SearchGateway,
  type SearchPredicate,
} from './searchGateway';

const EMPTY_CRITERIA: CandidateSearchCriteria = {
  dslVersion: '1',
  text: null,
  predicates: [],
  sort: { field: 'APPLIED_AT', direction: 'DESC' },
  limit: 25,
  cursor: null,
};

function errorMessage(error: unknown) {
  if (!(error instanceof SearchGatewayError)) {
    return 'Search is temporarily unavailable. Keep your query and try again.';
  }
  return {
    INTERPRETER_DISABLED:
      'AI interpretation is not enabled. Keyword search and Cmd/Ctrl+K still work.',
    INTERPRETER_UNAVAILABLE:
      'AI interpretation is temporarily unavailable. Your query is still here—try again or use keyword search.',
    INTERPRETER_QUOTA_EXCEEDED:
      'The AI search quota is temporarily exhausted. Use keyword search or try again later.',
    INTERPRETATION_INVALID:
      'That sentence could not be converted into safe filters. Rephrase it or use keyword search.',
    INTERPRETATION_RATE_LIMITED:
      'You have reached 10 AI interpretations this minute. Keep editing existing filters or wait a moment.',
    AMBIGUOUS_CURRENCY:
      'Add a currency to the compensation request, for example “under 40 LPA” or “under USD 80,000”.',
    SEARCH_INVALID: 'One or more filters are invalid. Review the values and try again.',
  }[error.code];
}

function formatExperience(months: number) {
  const years = Math.floor(months / 12);
  const remainder = months % 12;
  return `${years}y${remainder ? ` ${remainder}m` : ''}`;
}

function formatCompensation(candidate: CandidateApplicationSummary) {
  const value = candidate.expectedCompensation;
  if (!value) return 'Expected CTC unavailable';
  if (value.currency === 'INR') {
    return `₹${(value.minorUnits / 10_000_000).toFixed(2)} LPA expected`;
  }
  return `${value.currency} ${(value.minorUnits / 100).toLocaleString()} expected`;
}

function displayPredicateValue(predicate: SearchPredicate) {
  if (predicate.currency === 'INR' && predicate.field.includes('COMPENSATION')) {
    if (predicate.value === '') return '';
    return String(Number(predicate.value) / 10_000_000);
  }
  return predicate.value;
}

function storedPredicateValue(predicate: SearchPredicate, displayValue: string) {
  if (predicate.currency === 'INR' && predicate.field.includes('COMPENSATION')) {
    if (displayValue.trim() === '') return '';
    return String(Math.round(Number(displayValue) * 10_000_000));
  }
  return displayValue;
}

function SearchResults({ results }: { readonly results: readonly CandidateApplicationSummary[] }) {
  if (results.length === 0) {
    return (
      <div className="search-empty-state">
        <strong>No candidates match these filters.</strong>
        <span>Remove a filter or broaden the wording, then search again.</span>
      </div>
    );
  }

  return (
    <div className="search-result-list" aria-label="Candidate search results">
      {results.map((candidate) => (
        <article className="search-result-card" key={candidate.applicationId}>
          <span className="candidate-initials" aria-hidden="true">
            {candidate.candidateName
              .split(' ')
              .map((part) => part[0])
              .join('')}
          </span>
          <div className="search-result-identity">
            <strong>{candidate.candidateName}</strong>
            <span>
              {candidate.currentTitle || 'Title not provided'} ·{' '}
              {candidate.currentCompany || 'Company not provided'}
            </span>
            <small>{candidate.location || 'Location not provided'}</small>
          </div>
          <div className="search-result-job">
            <strong>{candidate.jobTitle}</strong>
            <span>{candidate.stage}</span>
          </div>
          <div className="search-result-facts">
            <strong>{formatExperience(candidate.totalExperienceMonths)}</strong>
            <span>{formatCompensation(candidate)}</span>
          </div>
          <div className="candidate-skill-list">
            {candidate.skills.slice(0, 3).map((skill) => (
              <span key={skill}>{skill}</span>
            ))}
          </div>
        </article>
      ))}
    </div>
  );
}

export function SearchWorkspace({ searchGateway }: { readonly searchGateway: SearchGateway }) {
  const initialQuery = useMemo(
    () => new URLSearchParams(window.location.search).get('q') ?? '',
    [],
  );
  const [keyword, setKeyword] = useState(initialQuery);
  const [naturalQuery, setNaturalQuery] = useState('');
  const [criteria, setCriteria] = useState<CandidateSearchCriteria | null>(null);
  const [chips, setChips] = useState<readonly SearchFilterChip[]>([]);
  const [warnings, setWarnings] = useState<readonly string[]>([]);
  const [results, setResults] = useState<readonly CandidateApplicationSummary[] | null>(null);
  const [status, setStatus] = useState<'IDLE' | 'INTERPRETING' | 'SEARCHING'>('IDLE');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const handleCommand = (event: Event) => {
      const query = (event as CustomEvent<string>).detail;
      setKeyword(query);
      void execute({ ...EMPTY_CRITERIA, text: query });
    };
    window.addEventListener('talon:command-search', handleCommand);
    if (initialQuery.trim().length >= 2) {
      void execute({ ...EMPTY_CRITERIA, text: initialQuery.trim() });
    }
    return () => window.removeEventListener('talon:command-search', handleCommand);
    // Initial URL search is intentionally executed only on mount.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchGateway]);

  async function execute(nextCriteria: CandidateSearchCriteria) {
    setStatus('SEARCHING');
    setError(null);
    try {
      const page = await searchGateway.query(nextCriteria);
      setResults(page.results);
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setStatus('IDLE');
    }
  }

  async function searchKeyword(event: FormEvent) {
    event.preventDefault();
    const text = keyword.trim();
    if (text.length < 2) {
      setError('Enter at least two characters for keyword search.');
      return;
    }
    setCriteria(null);
    setChips([]);
    setWarnings([]);
    await execute({ ...EMPTY_CRITERIA, text });
  }

  async function interpret(event: FormEvent) {
    event.preventDefault();
    if (!naturalQuery.trim()) return;
    setStatus('INTERPRETING');
    setError(null);
    try {
      const interpreted = await searchGateway.interpret({
        query: naturalQuery.trim(),
        locale: navigator.language,
        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      });
      setCriteria(interpreted.criteria);
      setChips(interpreted.chips);
      setWarnings(interpreted.warnings);
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setStatus('IDLE');
    }
  }

  function updatePredicate(index: number, displayValue: string) {
    if (!criteria) return;
    const predicates = criteria.predicates.map((predicate, predicateIndex) =>
      predicateIndex === index
        ? { ...predicate, value: storedPredicateValue(predicate, displayValue) }
        : predicate,
    );
    setCriteria({ ...criteria, predicates });
  }

  function removePredicate(index: number) {
    if (!criteria) return;
    setCriteria({
      ...criteria,
      predicates: criteria.predicates.filter((_, predicateIndex) => predicateIndex !== index),
    });
    setChips(chips.filter((_, chipIndex) => chipIndex !== index));
  }

  function resetInterpretation() {
    setCriteria(null);
    setChips([]);
    setWarnings([]);
    setResults(null);
    setError(null);
  }

  return (
    <section className="search-workspace" aria-label="Candidate search">
      <header className="search-workspace-heading">
        <div>
          <p className="eyebrow">Candidate discovery</p>
          <h2>Ask broadly. Search precisely.</h2>
          <p>
            Keyword search stays deterministic. AI turns a sentence into reviewable filters—it never
            runs a hidden query.
          </p>
        </div>
        <span className="search-shortcut-hint">Cmd/Ctrl + K</span>
      </header>

      <div className="search-duo">
        <form className="keyword-search-panel" onSubmit={(event) => void searchKeyword(event)}>
          <div className="search-panel-label">
            <Search aria-hidden="true" size={17} />
            <span>
              <strong>Keyword search</strong>
              <small>Names, skills, titles, companies, and email</small>
            </span>
          </div>
          <div className="search-input-row">
            <input
              aria-label="Keyword candidate search"
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="Try Java, Priya, or product designer"
            />
            <button className="secondary-button" disabled={status !== 'IDLE'} type="submit">
              Search
            </button>
          </div>
        </form>

        <form className="ai-search-panel" onSubmit={(event) => void interpret(event)}>
          <div className="search-panel-label">
            <Sparkles aria-hidden="true" size={17} />
            <span>
              <strong>AI filter builder</strong>
              <small>Groq translates your sentence; Talon validates every filter</small>
            </span>
          </div>
          <ol className="ai-search-steps" aria-label="AI search steps">
            <li className="active">Describe</li>
            <li className={criteria ? 'active' : undefined}>Review filters</li>
            <li className={criteria && results ? 'active' : undefined}>Search</li>
          </ol>
          <textarea
            aria-label="Describe candidates to find"
            maxLength={500}
            rows={3}
            value={naturalQuery}
            onChange={(event) => setNaturalQuery(event.target.value)}
            placeholder="Candidates in Bengaluru with 5+ years of Java and expected CTC below 40 LPA"
          />
          <div className="search-example-row">
            <span>Try an example</span>
            <button
              type="button"
              onClick={() =>
                setNaturalQuery(
                  'Senior Java candidates in Bengaluru with at least 5 years of experience',
                )
              }
            >
              Use senior Java example
            </button>
            <button
              type="button"
              onClick={() =>
                setNaturalQuery('Product designers available within 30 days below 35 LPA')
              }
            >
              Use product design example
            </button>
          </div>
          <div className="ai-search-action">
            <span>{naturalQuery.length}/500</span>
            <button
              className="primary-button"
              disabled={status !== 'IDLE' || !naturalQuery.trim()}
              type="submit"
            >
              {status === 'INTERPRETING' ? 'Building filters…' : 'Build AI filters'}
              <ArrowRight aria-hidden="true" size={15} />
            </button>
          </div>
        </form>
      </div>

      {error ? (
        <p className="search-message error-state" role="alert">
          {error}
        </p>
      ) : null}

      {criteria ? (
        <section className="interpreted-search" aria-label="Review AI filters">
          <header>
            <div>
              <p className="eyebrow">Sentence → validated criteria</p>
              <h3>Review filters before searching</h3>
            </div>
            <div className="interpreted-search-actions">
              <button
                className="secondary-button"
                disabled={status !== 'IDLE'}
                onClick={resetInterpretation}
                type="button"
              >
                Start over
              </button>
              <button
                className="primary-button"
                disabled={status !== 'IDLE'}
                onClick={() => void execute(criteria)}
                type="button"
              >
                {status === 'SEARCHING' ? 'Searching…' : 'Search candidates'}
              </button>
            </div>
          </header>
          {criteria.text ? <p className="residual-keywords">Keywords: {criteria.text}</p> : null}
          <div className="filter-chip-list">
            {criteria.predicates.map((predicate, index) => (
              <label className="editable-filter-chip" key={`${predicate.field}-${index}`}>
                <span>{chips[index]?.label ?? predicate.field}</span>
                <input
                  aria-label={`${chips[index]?.label ?? predicate.field} value`}
                  value={displayPredicateValue(predicate)}
                  onChange={(event) => updatePredicate(index, event.target.value)}
                />
                {predicate.currency === 'INR' && predicate.field.includes('COMPENSATION') ? (
                  <small>LPA</small>
                ) : null}
                <button
                  aria-label={`Remove ${chips[index]?.label ?? predicate.field} filter`}
                  onClick={() => removePredicate(index)}
                  type="button"
                >
                  <X aria-hidden="true" size={14} />
                </button>
              </label>
            ))}
          </div>
          {warnings.map((warning) => (
            <p className="search-warning" key={warning}>
              {warning}
            </p>
          ))}
        </section>
      ) : null}

      {status === 'SEARCHING' ? (
        <p className="search-message" role="status">
          Searching candidates…
        </p>
      ) : null}
      {results ? (
        <section className="search-results" aria-label="Search results">
          <header>
            <h3>Candidate matches</h3>
            <span>{results.length} results</span>
          </header>
          <SearchResults results={results} />
        </section>
      ) : null}
    </section>
  );
}
