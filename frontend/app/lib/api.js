// All calls hit same-origin /gateway/* which next.config.js proxies to the gateway.
const BASE = '/gateway';

async function req(path, options = {}) {
  const res = await fetch(BASE + path, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    cache: 'no-store',
  });
  const text = await res.text();
  let body = null;
  if (text) {
    try { body = JSON.parse(text); } catch { body = text; }
  }
  if (!res.ok) {
    const err = new Error((body && body.message) || `Request failed (${res.status})`);
    err.status = res.status;
    err.body = body;
    throw err;
  }
  return body;
}

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
