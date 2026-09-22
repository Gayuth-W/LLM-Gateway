/**
 * Client-side session storage.
 *
 * The token lives in localStorage, which means it is readable by any script that
 * gets injected into this origin. The stronger option is an httpOnly cookie set
 * by the gateway plus CSRF protection; that was left out deliberately, because it
 * pulls in cookie/CSRF handling on a service that is otherwise a stateless bearer
 * API. The mitigation actually in place is the short token TTL. Worth knowing
 * about rather than assuming this is the finished answer.
 *
 * Nothing here is a security control. It decides which buttons to render; the
 * gateway decides what is allowed.
 */

const TOKEN_KEY = 'llmgw.token';
const USER_KEY = 'llmgw.user';

/** Must match Role.implied() on the server: ADMIN > OPERATOR > VIEWER. */
const RANK = { VIEWER: 0, OPERATOR: 1, ADMIN: 2 };

/** Dispatched when the gateway rejects our token, so the app can drop to /login. */
export const AUTH_EXPIRED = 'llmgw:auth-expired';

function safe(fn, fallback = null) {
  if (typeof window === 'undefined') return fallback;
  try { return fn(); } catch { return fallback; }
}

export function getToken() {
  return safe(() => window.localStorage.getItem(TOKEN_KEY));
}

export function getStoredUser() {
  return safe(() => {
    const raw = window.localStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) : null;
  });
}

export function saveSession(token, user) {
  safe(() => {
    window.localStorage.setItem(TOKEN_KEY, token);
    window.localStorage.setItem(USER_KEY, JSON.stringify(user));
  });
}

export function clearSession() {
  safe(() => {
    window.localStorage.removeItem(TOKEN_KEY);
    window.localStorage.removeItem(USER_KEY);
  });
}

export function notifyExpired() {
  safe(() => window.dispatchEvent(new CustomEvent(AUTH_EXPIRED)));
}

/**
 * Whether `role` reaches `required`, mirroring the server-side hierarchy.
 * Used only to hide controls that would come back 403 anyway — showing a button
 * that always fails is worse than not showing it, but hiding one is not a
 * substitute for the server check.
 */
export function roleAllows(role, required) {
  if (!role) return false;
  const have = RANK[role];
  const need = RANK[required];
  return have !== undefined && need !== undefined && have >= need;
}
