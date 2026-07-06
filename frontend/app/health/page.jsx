'use client';

import { useEffect, useRef, useState } from 'react';
import { getHealth } from '../lib/api';

const TONE = { HEALTHY: 'ok', DEGRADED: 'warn', DOWN: 'down' };

export default function HealthPage() {
  const [models, setModels] = useState(null);
  const [error, setError] = useState(null);
  const [auto, setAuto] = useState(true);
  const [updatedAt, setUpdatedAt] = useState(null);
  const timer = useRef(null);

  async function load() {
    try { setModels(await getHealth()); setError(null); setUpdatedAt(new Date()); }
    catch (e) { setError(e.message); }
  }

  useEffect(() => {
    load();
    if (auto) timer.current = setInterval(load, 5000);
    return () => { if (timer.current) clearInterval(timer.current); };
  }, [auto]);

  return (
    <div className="page">
      <div className="page-head with-action">
        <div>
          <h1>Provider Health</h1>
          <p>Each model is probed on a schedule; status comes from a rolling error-rate window and a latency ring buffer.</p>
        </div>
        <div className="head-actions">
          {updatedAt && <span className="stamp mono">updated {updatedAt.toLocaleTimeString()}</span>}
          <label className="toggle inline">
            <input type="checkbox" checked={auto} onChange={(e) => setAuto(e.target.checked)} />
            <span>Auto-refresh 5s</span>
          </label>
          <button className="btn ghost" onClick={load}>Refresh</button>
        </div>
      </div>

      {error && <div className="banner bad">Could not load health: {error}</div>}
      {!models && !error && <div className="empty">Loading…</div>}

      <div className="health-grid">
        {models && models.map((m) => {
          const tone = TONE[m.status] || 'warn';
          return (
            <div key={m.model} className={`health-card ${tone}`}>
              <div className="hc-top">
                <span className="hc-model mono">{m.model}</span>
                <span className={`status-badge ${tone}`}>{m.status}</span>
              </div>
              <div className="hc-metrics">
                <Metric label="error rate" value={`${(m.errorRate * 100).toFixed(1)}%`} />
                <Metric label="p99 latency" value={`${Math.round(m.p99LatencyMs)} ms`} />
                <Metric label="samples" value={m.sampleCount} />
              </div>
              <div className="hc-bar">
                <span className={`hc-fill ${tone}`} style={{ width: `${Math.min(100, m.errorRate * 100)}%` }} />
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function Metric({ label, value }) {
  return (
    <div className="metric">
      <div className="metric-value mono">{value}</div>
      <div className="metric-label">{label}</div>
    </div>
  );
}
