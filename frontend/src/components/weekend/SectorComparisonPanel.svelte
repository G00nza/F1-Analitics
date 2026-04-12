<script>
  import { onMount } from 'svelte';

  export let sessions = [];

  const SESSION_LABELS = {
    FP1: 'FP1', FP2: 'FP2', FP3: 'FP3',
    QUALIFYING: 'Q', RACE: 'R', SPRINT: 'Sprint', SPRINT_QUALIFYING: 'SQ',
  };

  // Driver list comes from weekend summary
  let drivers = [];
  let driversLoading = true;

  let selectedKeyA = null;
  let selectedKeyB = null;
  let selectedDriver = null;

  let data = null;
  let loading = false;
  let error = null;

  onMount(async () => {
    try {
      const res = await fetch('/api/weekend/summary');
      if (res.ok) {
        const summary = await res.json();
        drivers = summary.drivers ?? [];
        if (drivers.length) selectedDriver = drivers[0].driverNumber;
      }
    } finally {
      driversLoading = false;
    }
  });

  // Set defaults when sessions list arrives
  $: if (sessions.length >= 2 && !selectedKeyA) {
    selectedKeyA = sessions[0].key;
    const q = sessions.find(s => s.type === 'QUALIFYING');
    selectedKeyB = q ? q.key : sessions[sessions.length - 1].key;
  }

  // Auto-fetch when all three selectors are ready
  $: if (selectedKeyA && selectedKeyB && selectedDriver && selectedKeyA !== selectedKeyB) {
    fetchComparison(selectedKeyA, selectedKeyB, selectedDriver);
  }

  let lastFetchKey = '';

  async function fetchComparison(keyA, keyB, driver) {
    const key = `${keyA}:${keyB}:${driver}`;
    if (key === lastFetchKey) return;
    lastFetchKey = key;

    loading = true; error = null; data = null;
    try {
      const res = await fetch(`/api/sessions/${keyA}/sector-comparison/${keyB}?driver=${encodeURIComponent(driver)}`);
      if (res.status === 404) {
        error = 'No sector data found for this driver / session combination.';
        return;
      }
      if (!res.ok) throw new Error(res.statusText);
      data = await res.json();
    } catch (e) {
      error = 'Failed to load sector comparison';
    } finally {
      loading = false;
    }
  }

  function sessionLabel(key) {
    if (!key) return '—';
    const s = sessions.find(s => s.key === key);
    if (!s) return String(key);
    return SESSION_LABELS[s.type] ?? s.name;
  }

  function fmtSector(ms) {
    if (ms == null) return '—';
    const s = Math.floor(ms / 1000);
    const f = String(ms % 1000).padStart(3, '0');
    return `${s}.${f}`;
  }

  function fmtDelta(ms) {
    if (ms == null) return '—';
    const abs = Math.abs(ms);
    const s = Math.floor(abs / 1000);
    const f = String(abs % 1000).padStart(3, '0');
    return `${ms < 0 ? '−' : '+'}${s}.${f}`;
  }

  function deltaClass(ms, sector, mostImproved, leastImproved) {
    if (ms == null) return '';
    if (sector === mostImproved)  return 'best-sector';
    if (sector === leastImproved) return 'worst-sector';
    if (ms < 0) return 'improved';
    if (ms > 0) return 'regressed';
    return '';
  }
</script>

<div class="controls">
  <label class="control-group">
    <span class="control-label">Session A (baseline)</span>
    <select bind:value={selectedKeyA} disabled={sessions.length === 0}>
      {#each sessions as s}
        <option value={s.key}>{SESSION_LABELS[s.type] ?? s.name}</option>
      {/each}
    </select>
  </label>

  <span class="arrow">→</span>

  <label class="control-group">
    <span class="control-label">Session B (comparison)</span>
    <select bind:value={selectedKeyB} disabled={sessions.length === 0}>
      {#each sessions as s}
        <option value={s.key}>{SESSION_LABELS[s.type] ?? s.name}</option>
      {/each}
    </select>
  </label>

  <label class="control-group">
    <span class="control-label">Driver</span>
    <select bind:value={selectedDriver} disabled={driversLoading || drivers.length === 0}>
      {#each drivers as d}
        <option value={d.driverNumber}>{d.driverCode}</option>
      {/each}
    </select>
  </label>
</div>

{#if selectedKeyA === selectedKeyB}
  <div class="state-msg">Select two different sessions to compare.</div>
{:else if loading}
  <div class="state-msg">Loading…</div>
{:else if error}
  <div class="state-msg error">{error}</div>
{:else if data}
  <h4 class="section-title">
    Sector Comparison — {data.driverCode}, {sessionLabel(selectedKeyA)} vs {sessionLabel(selectedKeyB)}
  </h4>
  <div class="table-wrap">
    <table>
      <thead>
        <tr>
          <th>Sector</th>
          <th>{sessionLabel(selectedKeyA)}</th>
          <th>{sessionLabel(selectedKeyB)}</th>
          <th>Delta</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        {#each data.sectors as row}
          {@const dc = deltaClass(row.deltaMs, row.sector, data.mostImprovedSector, data.leastImprovedSector)}
          <tr>
            <td class="sector-label">S{row.sector}</td>
            <td class="mono">{fmtSector(row.sessionAMs)}</td>
            <td class="mono">{fmtSector(row.sessionBMs)}</td>
            <td class="mono {dc}">{fmtDelta(row.deltaMs)}</td>
            <td class="badge-col">
              {#if row.sector === data.mostImprovedSector}
                <span class="badge best">best</span>
              {:else if row.sector === data.leastImprovedSector}
                <span class="badge worst">least</span>
              {/if}
            </td>
          </tr>
        {/each}
        <tr class="total-row">
          <td class="sector-label">Total</td>
          <td class="mono">—</td>
          <td class="mono">—</td>
          <td class="mono {data.totalDeltaMs != null && data.totalDeltaMs < 0 ? 'improved' : 'regressed'}">{fmtDelta(data.totalDeltaMs)}</td>
          <td></td>
        </tr>
      </tbody>
    </table>
  </div>
{/if}

<style>
  .controls {
    display: flex;
    align-items: flex-end;
    flex-wrap: wrap;
    gap: 1rem;
    margin-bottom: 1.75rem;
  }

  .control-group {
    display: flex;
    flex-direction: column;
    gap: 0.3rem;
  }

  .control-label {
    font-size: 0.7rem;
    font-weight: 600;
    letter-spacing: 0.07em;
    text-transform: uppercase;
    color: var(--text-secondary);
  }

  select {
    background: var(--bg-secondary);
    color: var(--text-primary);
    border: 1px solid #333;
    border-radius: 4px;
    padding: 0.35rem 0.7rem;
    font-size: 0.85rem;
    cursor: pointer;
    min-width: 110px;
  }

  select:focus { outline: 1px solid #E8002D; }

  .arrow {
    color: var(--text-secondary);
    font-size: 1.1rem;
    padding-bottom: 0.35rem;
  }

  .state-msg {
    padding: 3rem 2rem;
    color: var(--text-secondary);
    text-align: center;
  }
  .state-msg.error { color: #FF5252; }

  .section-title {
    font-size: 0.78rem;
    font-weight: 700;
    letter-spacing: 0.1em;
    text-transform: uppercase;
    color: var(--text-secondary);
    margin-bottom: 0.75rem;
    padding-bottom: 0.4rem;
    border-bottom: 1px solid #2a2a3a;
  }

  .table-wrap { overflow-x: auto; }

  table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.85rem;
    max-width: 520px;
  }

  th, td {
    padding: 0.5rem 0.75rem;
    text-align: left;
    border-bottom: 1px solid #1e1e2e;
    white-space: nowrap;
  }

  th {
    color: var(--text-secondary);
    font-size: 0.72rem;
    font-weight: 600;
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }

  .sector-label { font-weight: 700; color: var(--text-secondary); }

  .mono { font-family: monospace; }

  .improved    { color: #00C853; }
  .regressed   { color: #FF5252; }
  .best-sector { color: #00C853; font-weight: 700; }
  .worst-sector{ color: #FFB74D; }

  .badge-col { width: 60px; }

  .badge {
    display: inline-block;
    padding: 0.1rem 0.4rem;
    border-radius: 3px;
    font-size: 0.68rem;
    font-weight: bold;
    text-transform: uppercase;
    letter-spacing: 0.05em;
  }

  .badge.best  { background: #003d1a; color: #00C853; border: 1px solid #00C853; }
  .badge.worst { background: #2a1e00; color: #FFB74D; border: 1px solid #FFB74D; }

  .total-row td { border-top: 2px solid #2a2a3a; font-weight: 600; }

  tr:hover td { background: #1a1a2a; }
</style>
