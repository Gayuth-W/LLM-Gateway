'use client';

import { useState } from 'react';
import { chat, streamChat } from './lib/api';

const TEAMS = [
  { key: 'sk-enterprise-key', label: 'ACME Enterprise', hint: 'all models · $25/day · compliance enrichment' },
  { key: 'sk-internal-key', label: 'Internal Tools', hint: 'llama3.1 · gemma3:1b · $5/day' },
  { key: 'sk-free-key', label: 'Free Tier', hint: 'gemma3:1b only · 5 rpm · easy to throttle' },
  { key: '__custom__', label: 'Custom key…', hint: 'enter any team API key' },
];
const MODELS = ['llama3.1', 'gemma3', 'gemma3:1b'];

export default function Playground() {
  const [teamKey, setTeamKey] = useState(TEAMS[0].key);
  const [customKey, setCustomKey] = useState('');
  const [model, setModel] = useState(MODELS[0]);
  const [priority, setPriority] = useState('high');
  const [stream, setStream] = useState(false);
  const [prompt, setPrompt] = useState('Explain what an API gateway does, in two sentences.');

  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState(null); // { status, headers, content, error }
  const [live, setLive] = useState('');

  const apiKey = teamKey === '__custom__' ? customKey : teamKey;
  const selectedTeam = TEAMS.find((t) => t.key === teamKey);

  async function run() {
    setBusy(true);
    setResult(null);
    setLive('');
    try {
      if (stream) {
        let acc = '';
        try {
          await streamChat({ apiKey, model, prompt, priority },
            (delta) => { acc += delta; setLive(acc); },
            () => { });
          setResult({ status: 200, headers: {}, content: acc, error: null });
        } catch (e) {
          setResult({ status: e.status || 0, headers: {}, content: null, error: e.message });
        }
      } else {
        const r = await chat({ apiKey, model, prompt, priority });
        const content = r.ok && r.body && r.body.choices ? r.body.choices[0].message.content : null;
        const error = r.ok ? null : (r.body && r.body.error ? `${r.body.error} — ${r.body.message}` : `HTTP ${r.status}`);
        setResult({ status: r.status, headers: r.headers, content, error });
      }
    } finally {
      setBusy(false);
    }
  }

  const ok = result && result.status >= 200 && result.status < 300;

  return (
    <div className="page">
      <div className="page-head">
        <h1>Playground</h1>
        <p>Send a request through the gateway. Rejections come back with the reason — budget, model access, rate limit — so you can see the policy layer at work.</p>
      </div>

      <div className="play-grid">
        <section className="panel">
          <div className="panel-label">Request</div>

          <label className="field">
            <span className="field-label">Team</span>
            <select value={teamKey} onChange={(e) => setTeamKey(e.target.value)}>
              {TEAMS.map((t) => <option key={t.key} value={t.key}>{t.label}</option>)}
            </select>
            {selectedTeam && <span className="field-hint">{selectedTeam.hint}</span>}
          </label>

          {teamKey === '__custom__' && (
            <label className="field">
              <span className="field-label">API key</span>
              <input className="mono" value={customKey} placeholder="sk-…" onChange={(e) => setCustomKey(e.target.value)} />
            </label>
          )}

          <div className="field-row">
            <label className="field">
              <span className="field-label">Model</span>
              <select value={model} onChange={(e) => setModel(e.target.value)}>
                {MODELS.map((m) => <option key={m} value={m}>{m}</option>)}
              </select>
            </label>
            <label className="field">
              <span className="field-label">Priority</span>
              <select value={priority} onChange={(e) => setPriority(e.target.value)}>
                <option value="high">high</option>
                <option value="low">low</option>
              </select>
            </label>
          </div>

          <label className="field">
            <span className="field-label">Prompt</span>
            <textarea rows={5} value={prompt} onChange={(e) => setPrompt(e.target.value)} />
          </label>

          <label className="toggle">
            <input type="checkbox" checked={stream} onChange={(e) => setStream(e.target.checked)} />
            <span>Stream the response</span>
          </label>

          <button className="btn primary" onClick={run} disabled={busy || !apiKey || !prompt}>
            {busy ? 'Sending…' : 'Send request'}
          </button>
        </section>

        <section className="panel">
          <div className="panel-label">Response</div>

          {!result && !busy && !live && (
            <div className="empty">The reply, the model that served it, and token usage will appear here.</div>
          )}

          {result && (
            <div className="resp-meta">
              <span className={`status-pill ${ok ? 'ok' : 'bad'}`}>{result.status || '—'}</span>
              {result.headers && result.headers.provider && (
                <span className="meta-chip">served by <b className="mono">{result.headers.provider}</b></span>
              )}
              {result.headers && result.headers.fallback === 'true' && (
                <span className="meta-chip warn">fallback used</span>
              )}
              {result.headers && result.headers.tokens && (
                <span className="meta-chip">{result.headers.tokens} tokens</span>
              )}
            </div>
          )}

          {result && result.error && <div className="resp-error">{result.error}</div>}

          {(live || (result && result.content)) && (
            <pre className="resp-body">{live || result.content}</pre>
          )}
        </section>
      </div>
    </div>
  );
}
