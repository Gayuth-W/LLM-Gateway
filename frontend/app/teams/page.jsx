'use client';

import { useEffect, useState } from 'react';
import { getTeams, createTeam, updateLimits, updateBudget } from '../lib/api';


const EMPTY_CREATE = {
  apiKey: '', name: '', allowedModels: 'llama3.1,gemma3:1b',
  rpmLimit: 60, tpmLimit: 60000, lowPriorityRpm: 20,
  dailyBudgetUsd: 5, monthlyBudgetUsd: 100, enrichmentProfile: 'internal',
};

export default function TeamsPage() {
  const [teams, setTeams] = useState(null);
  const [error, setError] = useState(null);
  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState(EMPTY_CREATE);
  const [editing, setEditing] = useState(null); // team being edited
  const [msg, setMsg] = useState(null);

  async function load() {
    setError(null);
    try { setTeams(await getTeams()); }
    catch (e) { setError(e.message); }
  }
  useEffect(() => { load(); }, []);

  async function submitCreate() {
    setMsg(null);
    try {
      await createTeam({
        apiKey: form.apiKey.trim(),
        name: form.name.trim(),
        allowedModels: form.allowedModels.split(',').map((s) => s.trim()).filter(Boolean),
        rpmLimit: Number(form.rpmLimit),
        tpmLimit: Number(form.tpmLimit),
        lowPriorityRpm: Number(form.lowPriorityRpm),
        dailyBudgetUsd: Number(form.dailyBudgetUsd),
        monthlyBudgetUsd: Number(form.monthlyBudgetUsd),
        enrichmentProfile: form.enrichmentProfile.trim(),
      });
      setShowCreate(false);
      setForm(EMPTY_CREATE);
      setMsg('Team created.');
      load();
    } catch (e) { setMsg('Create failed: ' + e.message); }
  }

  async function saveEdit() {
    setMsg(null);
    try {
      await updateLimits(editing.id, {
        rpmLimit: Number(editing.rpmLimit),
        tpmLimit: Number(editing.tpmLimit),
        lowPriorityRpm: Number(editing.lowPriorityRpm),
      });
      await updateBudget(editing.id, {
        dailyBudgetUsd: Number(editing.dailyBudgetUsd),
        monthlyBudgetUsd: Number(editing.monthlyBudgetUsd),
      });
      setEditing(null);
      setMsg('Team updated.');
      load();
    } catch (e) { setMsg('Update failed: ' + e.message); }
  }

  return (
    <div className="page">
      <div className="page-head with-action">
        <div>
          <h1>Teams</h1>
          <p>Rate limits, budgets, and live spend per team. Changes apply on the next request.</p>
        </div>
        <div className="head-actions">
          <button className="btn ghost" onClick={load}>Refresh</button>
          <button className="btn primary" onClick={() => setShowCreate((v) => !v)}>New team</button>
        </div>
      </div>

      {msg && <div className="banner">{msg}</div>}
      {error && <div className="banner bad">Could not load teams: {error}</div>}

      {showCreate && (
        <section className="card create-card">
          <div className="card-title">Create team</div>
          <div className="form-grid">
            <Field label="API key"><input className="mono" value={form.apiKey} onChange={(e) => setForm({ ...form, apiKey: e.target.value })} placeholder="sk-…" /></Field>
            <Field label="Name"><input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="Team name" /></Field>
            <Field label="Allowed models (comma-sep)"><input className="mono" value={form.allowedModels} onChange={(e) => setForm({ ...form, allowedModels: e.target.value })} /></Field>
            <Field label="Enrichment profile"><input className="mono" value={form.enrichmentProfile} onChange={(e) => setForm({ ...form, enrichmentProfile: e.target.value })} /></Field>
            <Field label="RPM"><input type="number" value={form.rpmLimit} onChange={(e) => setForm({ ...form, rpmLimit: e.target.value })} /></Field>
            <Field label="TPM"><input type="number" value={form.tpmLimit} onChange={(e) => setForm({ ...form, tpmLimit: e.target.value })} /></Field>
            <Field label="Low-priority RPM"><input type="number" value={form.lowPriorityRpm} onChange={(e) => setForm({ ...form, lowPriorityRpm: e.target.value })} /></Field>
            <Field label="Daily budget (USD)"><input type="number" step="any" value={form.dailyBudgetUsd} onChange={(e) => setForm({ ...form, dailyBudgetUsd: e.target.value })} /></Field>
            <Field label="Monthly budget (USD)"><input type="number" step="any" value={form.monthlyBudgetUsd} onChange={(e) => setForm({ ...form, monthlyBudgetUsd: e.target.value })} /></Field>
          </div>
          <div className="form-actions">
            <button className="btn ghost" onClick={() => setShowCreate(false)}>Cancel</button>
            <button className="btn primary" onClick={submitCreate} disabled={!form.apiKey || !form.name}>Create team</button>
          </div>
        </section>
      )}

      <section className="card table-card">
        {!teams && !error && <div className="empty">Loading teams…</div>}
        {teams && (
          <table className="grid">
            <thead>
              <tr>
                <th>Team</th><th>Models</th><th className="num">RPM</th><th className="num">TPM</th>
                <th className="num">Daily budget</th><th>Spend today</th><th></th>
              </tr>
            </thead>
            <tbody>
              {teams.map((t) => {
                const util = Math.max(0, Math.min(1, t.dailyBudgetUtilisation || 0));
                return (
                  <tr key={t.id}>
                    <td>
                      <div className="team-name">{t.name}</div>
                      {t.budgetExhausted && <span className="tag down">budget exhausted</span>}
                    </td>
                    <td>
                      <div className="chips">
                        {(t.allowedModels || []).map((m) => <span key={m} className="chip mono">{m}</span>)}
                      </div>
                    </td>
                    <td className="num mono">{t.rpmLimit}</td>
                    <td className="num mono">{t.tpmLimit}</td>
                    <td className="num mono">{usd(t.dailyBudgetUsd)}</td>
                    <td>
                      <div className="spend mono">{usd(t.spentTodayUsd)}</div>
                      <div className="util"><span className={`util-fill${util >= 1 ? ' full' : ''}`} style={{ width: `${util * 100}%` }} /></div>
                    </td>
                    <td className="num">
                      <button className="btn tiny" onClick={() => setEditing({
                        id: t.id, name: t.name,
                        rpmLimit: t.rpmLimit, tpmLimit: t.tpmLimit, lowPriorityRpm: t.lowPriorityRpm,
                        dailyBudgetUsd: t.dailyBudgetUsd, monthlyBudgetUsd: t.monthlyBudgetUsd,
                      })}>Edit</button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </section>

      {editing && (
        <div className="modal-scrim" onClick={() => setEditing(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="card-title">Edit — {editing.name}</div>
            <div className="form-grid two">
              <Field label="RPM"><input type="number" value={editing.rpmLimit} onChange={(e) => setEditing({ ...editing, rpmLimit: e.target.value })} /></Field>
              <Field label="TPM"><input type="number" value={editing.tpmLimit} onChange={(e) => setEditing({ ...editing, tpmLimit: e.target.value })} /></Field>
              <Field label="Low-priority RPM"><input type="number" value={editing.lowPriorityRpm} onChange={(e) => setEditing({ ...editing, lowPriorityRpm: e.target.value })} /></Field>
              <Field label="Daily budget (USD)"><input type="number" step="any" value={editing.dailyBudgetUsd} onChange={(e) => setEditing({ ...editing, dailyBudgetUsd: e.target.value })} /></Field>
              <Field label="Monthly budget (USD)"><input type="number" step="any" value={editing.monthlyBudgetUsd} onChange={(e) => setEditing({ ...editing, monthlyBudgetUsd: e.target.value })} /></Field>
            </div>
            <div className="form-actions">
              <button className="btn ghost" onClick={() => setEditing(null)}>Cancel</button>
              <button className="btn primary" onClick={saveEdit}>Save changes</button>
            </div>
            <div className="modal-note">Updating the budget also clears a prior “exhausted” flag.</div>
          </div>
        </div>
      )}
    </div>
  );
}

function Field({ label, children }) {
  return (
    <label className="field">
      <span className="field-label">{label}</span>
      {children}
    </label>
  );
}
