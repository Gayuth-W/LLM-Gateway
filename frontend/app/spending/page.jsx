'use client';

import { useEffect, useState } from 'react';
import { getSpending } from '../lib/api';

function usd(v) {
  const n = Number(v);
  return Number.isNaN(n) ? '—' : '$' + n.toFixed(6);
}

export default function SpendingPage() {
  const [report, setReport] = useState(null);
  const [error, setError] = useState(null);
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');

  async function load() {
    setError(null);
    try { setReport(await getSpending(from || undefined, to || undefined)); }
    catch (e) { setError(e.message); }
  }
  useEffect(() => { load(); }, []);

  const rows = report ? report.rows : [];

  return (
    <div className="page">
      <div className="page-head with-action">
        <div>
          <h1>Spending</h1>
          <p>Per-team, per-model cost from the durable Postgres rollup. Live spend flushes here on a schedule.</p>
        </div>
        <div className="head-actions">
          <label className="field inline">
            <span className="field-label">From</span>
            <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
          </label>
          <label className="field inline">
            <span className="field-label">To</span>
            <input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
          </label>
          <button className="btn primary" onClick={load}>Run report</button>
        </div>
      </div>

      {error && <div className="banner bad">Could not load spending: {error}</div>}

      {report && (
        <div className="total-strip">
          <div className="total-label">Total ({report.from} → {report.to})</div>
          <div className="total-value mono">{usd(report.totalUsd)}</div>
        </div>
      )}

      <section className="card table-card">
        {!report && !error && <div className="empty">Loading…</div>}
        {report && rows.length === 0 && (
          <div className="empty">No spend recorded in this range. Send some requests, then run the report (allow ~60s for the flush).</div>
        )}
        {rows.length > 0 && (
          <table className="grid">
            <thead>
              <tr>
                <th>Team</th><th>Model</th><th>Provider</th>
                <th className="num">Input</th><th className="num">Output</th><th className="num">Cost</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r, i) => (
                <tr key={i}>
                  <td>{r.teamName}</td>
                  <td className="mono">{r.model}</td>
                  <td className="mono muted">{r.provider}</td>
                  <td className="num mono">{r.inputTokens}</td>
                  <td className="num mono">{r.outputTokens}</td>
                  <td className="num mono">{usd(r.costUsd)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
