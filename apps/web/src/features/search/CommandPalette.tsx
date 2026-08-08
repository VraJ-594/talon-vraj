import { BriefcaseBusiness, Search, UserRound, X } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useLocation } from 'wouter';

import type { CommandSearchItem, SearchGateway } from './searchGateway';

export function CommandPalette({ searchGateway }: { readonly searchGateway: SearchGateway }) {
  const [, navigate] = useLocation();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<readonly CommandSearchItem[]>([]);
  const [loading, setLoading] = useState(false);
  const input = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    const listener = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        setOpen(true);
      }
      if (event.key === 'Escape') setOpen(false);
    };
    window.addEventListener('keydown', listener);
    return () => window.removeEventListener('keydown', listener);
  }, []);

  useEffect(() => {
    if (open) queueMicrotask(() => input.current?.focus());
  }, [open]);

  useEffect(() => {
    if (!open || query.trim().length < 2) {
      setResults([]);
      setLoading(false);
      return undefined;
    }
    let active = true;
    const timer = window.setTimeout(() => {
      setLoading(true);
      void searchGateway
        .command(query.trim())
        .then((items) => {
          if (active) setResults(items);
        })
        .catch(() => {
          if (active) setResults([]);
        })
        .finally(() => {
          if (active) setLoading(false);
        });
    }, 160);
    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [open, query, searchGateway]);

  function choose(item: CommandSearchItem) {
    setOpen(false);
    setQuery('');
    navigate(`/search?q=${encodeURIComponent(item.label)}`);
    window.dispatchEvent(new CustomEvent('talon:command-search', { detail: item.label }));
  }

  if (!open) return null;

  return (
    <div className="command-palette-backdrop" onMouseDown={() => setOpen(false)}>
      <section
        aria-label="Search candidates and jobs"
        aria-modal="true"
        className="command-palette"
        onMouseDown={(event) => event.stopPropagation()}
        role="dialog"
      >
        <header>
          <Search aria-hidden="true" size={19} />
          <input
            aria-label="Command search"
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search candidates and jobs…"
            ref={input}
            value={query}
          />
          <button aria-label="Close command search" onClick={() => setOpen(false)} type="button">
            <X aria-hidden="true" size={16} />
          </button>
        </header>
        <div className="command-palette-results">
          {query.trim().length < 2 ? (
            <p>Type at least two characters. This search never calls AI.</p>
          ) : loading ? (
            <p role="status">Searching…</p>
          ) : results.length ? (
            results.map((item) => {
              const Icon = item.type === 'CANDIDATE' ? UserRound : BriefcaseBusiness;
              return (
                <button key={`${item.type}-${item.id}`} onClick={() => choose(item)} type="button">
                  <span className="command-result-icon">
                    <Icon aria-hidden="true" size={16} />
                  </span>
                  <span>
                    <strong>{item.label}</strong>
                    <small>{item.description || item.type.toLowerCase()}</small>
                  </span>
                  <kbd>↵</kbd>
                </button>
              );
            })
          ) : (
            <p>No matching candidates or open jobs.</p>
          )}
        </div>
        <footer>
          <span>Deterministic workspace search</span>
          <kbd>Esc</kbd>
        </footer>
      </section>
    </div>
  );
}
