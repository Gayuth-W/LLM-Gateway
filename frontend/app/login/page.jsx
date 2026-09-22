'use client';

import { useState } from 'react';
import { useAuth } from '../components/AuthProvider';

/**
 * The seeded accounts from V4__admin_users.sql. Listing them on the sign-in
 * screen is only defensible because this is a local demo stack with
 * deliberately well-known credentials — the same reasoning as the seeded team
 * API keys in the README. It doubles as the clearest statement of what the
 * three roles actually mean.
 */
const DEMO_ACCOUNTS = [
  { username: 'admin', password: 'admin123', role: 'ADMIN', can: 'Everything, including issuing team API keys' },
  { username: 'operator', password: 'operator123', role: 'OPERATOR', can: 'Reads, plus limits, budgets and alerts' },
  { username: 'viewer', password: 'viewer123', role: 'VIEWER', can: 'Reads only' },
];

export default function LoginPage() {
  const { signIn } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    if (busy) return;
    setError(null);
    setBusy(true);
    try {
      await signIn(username.trim(), password);
      // AuthProvider's guard moves us to the console once status flips.
    } catch (err) {
      setError(err.status === 401
        ? 'Incorrect username or password.'
        : `Could not sign in: ${err.message}`);
      setBusy(false);
    }
  }

  function useAccount(account) {
    setUsername(account.username);
    setPassword(account.password);
    setError(null);
  }

  return (
    <div className="auth-screen">
      <div className="auth-card">
        <div className="auth-brand">
          <div className="brand-name">LLM&nbsp;Gateway</div>
          <div className="brand-sub">operator console</div>
        </div>

        <form className="auth-form" onSubmit={submit}>
          <label className="field">
            <span className="field-label">Username</span>
            <input
              className="mono"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoComplete="username"
              autoFocus
            />
          </label>

          <label className="field">
            <span className="field-label">Password</span>
            <input
              className="mono"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
            />
          </label>

          {error && <div className="banner bad auth-error">{error}</div>}

          <button
            className="btn primary block"
            type="submit"
            disabled={busy || !username || !password}
          >
            {busy ? 'Signing in…' : 'Sign in'}
          </button>
        </form>

        <div className="auth-note">
          Signing in issues a 1-hour token. The playground still uses a team API
          key — that is a separate credential.
        </div>
      </div>

      <div className="auth-accounts">
        <div className="panel-label">Seeded accounts</div>
        <table className="grid demo-accounts">
          <tbody>
            {DEMO_ACCOUNTS.map((a) => (
              <tr key={a.username}>
                <td>
                  <div className="mono">{a.username}</div>
                  <div className="mono muted">{a.password}</div>
                </td>
                <td>
                  <span className={`role-badge ${a.role.toLowerCase()}`}>{a.role}</span>
                  <div className="account-can">{a.can}</div>
                </td>
                <td className="num">
                  <button className="btn tiny" type="button" onClick={() => useAccount(a)}>
                    Use
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="auth-hint">
          Local development credentials, seeded by Flyway. Rotate them before the
          stack is reachable from anywhere but localhost.
        </div>
      </div>
    </div>
  );
}
