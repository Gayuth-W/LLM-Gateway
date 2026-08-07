// All calls hit same-origin /gateway/* which next.config.js proxies to the gateway.
import { getToken, clearSession, notifyExpired } from './auth';

const BASE = '/gateway';

/**
 * Two credentials live in this console and they are not interchangeable:
 *
 *   - the OPERATOR JWT attached below, which authorises /admin/** and /auth/me;
 *   - a TEAM API key, typed into the playground, which authorises /v1/**.
 *
 * `req` is for the first. `chat`/`streamChat` at the bottom of this file are for
 * the second and deliberately do not attach the operator token.
 */
async function req(path, options = {}) {
  const { skipAuthRedirect = false, ...init } = options;
  const token = getToken();
  const res = await fetch(BASE + path, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(init.headers || {}),
    },
    cache: 'no-store',
  });
  const text = await res.text();
  let body = null;
  if (text) {
    try { body = JSON.parse(text); } catch { body = text; }
  }
  if (!res.ok) {
    // 401 means the token is missing, expired, or bogus. Drop the session and let
    // the app fall back to the sign-in screen rather than showing a broken page.
    // 403 is different: the session is fine, the role just is not enough, so it
    // surfaces as an ordinary error the page can explain.
    if (res.status === 401 && !skipAuthRedirect) {
      clearSession();
      notifyExpired();
    }
    const err = new Error((body && body.message) || `Request failed (${res.status})`);
    err.status = res.status;
    err.body = body;
    throw err;
  }
  return body;
}

// ---- operator session ----

export function login(username, password) {
  return req('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
    // A failed sign-in is a 401 by definition; it should render an inline message,
    // not trigger the expired-session redirect.
    skipAuthRedirect: true,
  });
}

export function getMe() { return req('/auth/me'); }

export function getGatewayInfo() { return req('/'); }
export function getTeams() { return req('/admin/teams'); }
export function createTeam(body) {
  return req('/admin/teams', { method: 'POST', body: JSON.stringify(body) });
}
export function updateLimits(id, body) {
  return req(`/admin/teams/${id}/limits`, { method: 'PATCH', body: JSON.stringify(body) });
}
export function updateBudget(id, body) {
  return req(`/admin/teams/${id}/budget`, { method: 'PATCH', body: JSON.stringify(body) });
}
export function getHealth() { return req('/admin/providers/health'); }
export function getSpending(from, to) {
  const qs = new URLSearchParams();
  if (from) qs.set('from', from);
  if (to) qs.set('to', to);
  const q = qs.toString();
  return req('/admin/spending' + (q ? `?${q}` : ''));
}

/**
 * Non-streaming completion. Returns status + headers + body WITHOUT throwing on 4xx,
 * because the console wants to display rejections (402/403/429) as first-class outcomes.
 */
export async function chat({ apiKey, model, prompt, priority = 'high' }) {
  const res = await fetch(BASE + '/v1/chat/completions', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(apiKey ? { Authorization: `Bearer ${apiKey}` } : {}),
      'X-Priority': priority,
    },
    body: JSON.stringify({ model, messages: [{ role: 'user', content: prompt }], stream: false }),
    cache: 'no-store',
  });
  const text = await res.text();
  let body = null;
  if (text) { try { body = JSON.parse(text); } catch { body = text; } }
  return {
    status: res.status,
    ok: res.ok,
    body,
    headers: {
      provider: res.headers.get('X-Provider-Used'),
      fallback: res.headers.get('X-Gateway-Fallback'),
      tokens: res.headers.get('X-Request-Tokens'),
    },
  };
}

/**
 * Streaming completion (SSE). Calls onDelta for each token chunk and onDone at the end.
 * Throws on a non-2xx status so the caller can surface the rejection.
 */
export async function streamChat({ apiKey, model, prompt, priority = 'high' }, onDelta, onDone) {
  // Streaming goes through the Route Handler at /api/chat so the stream is not buffered.
  const res = await fetch('/api/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      apiKey,
      priority,
      payload: { model, messages: [{ role: 'user', content: prompt }], stream: true },
    }),
    cache: 'no-store',
  });

  if (!res.ok) {
    const text = await res.text();
    let body = null;
    if (text) { try { body = JSON.parse(text); } catch { } }
    const err = new Error((body && body.message) || `Request failed (${res.status})`);
    err.status = res.status;
    err.body = body;
    throw err;
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split('\n');
    buffer = lines.pop() || '';
    for (const line of lines) {
      const t = line.trim();
      if (!t.startsWith('data:')) continue;
      const data = t.slice(5).trim();
      if (data === '[DONE]') { onDone && onDone(); return; }
      try {
        const chunk = JSON.parse(data);
        const delta = chunk.choices && chunk.choices[0] && chunk.choices[0].delta && chunk.choices[0].delta.content;
        if (delta) onDelta(delta);
      } catch { /* ignore keep-alive / non-JSON lines */ }
    }
  }
  onDone && onDone();
}
