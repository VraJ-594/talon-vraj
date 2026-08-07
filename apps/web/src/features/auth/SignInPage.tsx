import { Eye, EyeOff } from 'lucide-react';
import { useState, type FormEvent } from 'react';

import { isAuthProblem, type AuthProblemCode, type LoginCredentials } from './authGateway';

const LOGIN_PROBLEM_MESSAGES: Partial<Record<AuthProblemCode, string>> = {
  INVALID_CREDENTIALS:
    'We couldn’t sign you in with those credentials. Check your email and password and try again.',
  RATE_LIMITED: 'Too many sign-in attempts. Wait a few minutes and try again.',
  ACCOUNT_LOCKED:
    'Sign-in is temporarily locked. Try again later or contact your workspace administrator.',
  API_UNAVAILABLE: 'Talon can’t reach the sign-in service right now. Try again.',
};

type SignInPageProps = {
  readonly statusMessage?: string | null;
  readonly onLogin: (credentials: LoginCredentials) => Promise<void>;
};

export function SignInPage({ onLogin, statusMessage = null }: SignInPageProps) {
  const [passwordVisible, setPasswordVisible] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const fields = new FormData(event.currentTarget);
    setSubmitting(true);
    setErrorMessage(null);

    try {
      await onLogin({
        email: String(fields.get('email') ?? ''),
        password: String(fields.get('password') ?? ''),
      });
    } catch (error) {
      const problemMessage = isAuthProblem(error) ? LOGIN_PROBLEM_MESSAGES[error.code] : undefined;

      if (problemMessage) {
        setErrorMessage(problemMessage);
      } else {
        throw error;
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="sign-in-page">
      <section className="sign-in-story" aria-label="Talon recruiting workspace">
        <a className="sign-in-brand" href="/sign-in" aria-label="Talon sign in">
          <span className="brand-mark brand-mark-inverse" aria-hidden="true">
            <span />
          </span>
          <span>Talon</span>
        </a>

        <div className="sign-in-story-copy">
          <p className="eyebrow">One recruiting workspace</p>
          <h2>Hiring, coordinated.</h2>
          <p>Bring every application, resume, and search into one deliberate workflow.</p>

          <article className="candidate-pulse" aria-label="Example application status">
            <span className="avatar avatar-violet" aria-hidden="true">
              AP
            </span>
            <span>
              <strong>Ana Petrova</strong>
              <small>Resume ready for review</small>
            </span>
            <span className="candidate-pulse-status">Imported</span>
          </article>
        </div>

        <dl className="sign-in-metrics">
          <div>
            <dt>Application import</dt>
            <dd>2,000 rows</dd>
          </div>
          <div>
            <dt>Resume protection</dt>
            <dd>Private by default</dd>
          </div>
          <div>
            <dt>Search modes</dt>
            <dd>Keyword + natural</dd>
          </div>
        </dl>
      </section>

      <section className="sign-in-panel" aria-labelledby="sign-in-title">
        <div className="sign-in-card">
          <p className="eyebrow">Workspace access</p>
          <h1 id="sign-in-title">Welcome back</h1>
          <p className="sign-in-intro">Sign in to your Talon workspace.</p>
          {statusMessage ? (
            <p className="auth-status" role="status">
              {statusMessage}
            </p>
          ) : null}
          {errorMessage ? (
            <p className="auth-error" role="alert">
              {errorMessage}
            </p>
          ) : null}
          <form onSubmit={submit}>
            <label>
              <span>Work email</span>
              <input
                name="email"
                type="email"
                autoComplete="email"
                placeholder="admin@talon.demo"
              />
            </label>
            <label>
              <span>Password</span>
              <span className="password-field">
                <input
                  name="password"
                  type={passwordVisible ? 'text' : 'password'}
                  autoComplete="current-password"
                  placeholder="Enter your password"
                />
                <button
                  type="button"
                  aria-label={passwordVisible ? 'Hide password' : 'Show password'}
                  onClick={() => setPasswordVisible((visible) => !visible)}
                >
                  {passwordVisible ? (
                    <EyeOff aria-hidden="true" size={18} />
                  ) : (
                    <Eye aria-hidden="true" size={18} />
                  )}
                </button>
              </span>
            </label>
            <button className="sign-in-submit" type="submit" disabled={submitting}>
              {submitting ? 'Signing in…' : 'Sign in'}
            </button>
          </form>
          <p className="fixture-note">
            Demo workspace: <strong>admin@talon.demo</strong>. Use a temporary local password.
          </p>
        </div>
      </section>
    </main>
  );
}
