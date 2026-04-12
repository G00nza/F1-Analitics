<script>
  import { onMount, onDestroy } from 'svelte';
  import { sessionState } from '../../stores/session.js';

  const COMPOUND_COLORS = { SOFT: '#E8002D', MEDIUM: '#FFF200', HARD: '#FFFFFF', INTER: '#39B54A', WET: '#0067FF' };
  const COMPOUND_TEXT   = { SOFT: '#111',    MEDIUM: '#111',    HARD: '#111',    INTER: '#111',    WET: '#fff'    };

  let liveTab = 'tracker';

  // ── Tracker state ──────────────────────────────────────────────────────────
  let trackerData    = null;
  let trackerLoading = false;
  let trackerError   = null;

  // ── Alerts state ──────────────────────────────────────────────────────────
  let alertsData    = null;
  let alertsLoading = false;
  let alertsError   = null;

  let pollInterval = null;

  $: sessionKey = $sessionState?.sessionKey;

  $: if (sessionKey) {
    loadTracker(sessionKey);
    loadAlerts(sessionKey);
    startPolling(sessionKey);
  }

  function startPolling(key) {
    if (pollInterval) clearInterval(pollInterval);
    pollInterval = setInterval(() => {
      loadTracker(key);
      if (liveTab === 'alerts') loadAlerts(key);
    }, 15000);
  }

  onDestroy(() => { if (pollInterval) clearInterval(pollInterval); });

  async function loadTracker(key) {
    if (!key) return;
    trackerLoading = true; trackerError = null;
    try {
      const res = await fetch(`/api/sessions/${key}/strategy/tracker`);
      if (!res.ok) throw new Error(res.statusText);
      trackerData = await res.json();
    } catch (e) { trackerError = 'Failed to load strategy tracker'; }
    finally { trackerLoading = false; }
  }

  async function loadAlerts(key) {
    if (!key) return;
    alertsLoading = true; alertsError = null;
    try {
      const res = await fetch(`/api/sessions/${key}/strategy/alerts`);
      if (!res.ok) throw new Error(res.statusText);
      alertsData = await res.json();
    } catch (e) { alertsError = 'Failed to load strategy alerts'; }
    finally { alertsLoading = false; }
  }

  function compoundBg(c) { return COMPOUND_COLORS[c] ?? '#555'; }
  function compoundFg(c) { return COMPOUND_TEXT[c]   ?? '#fff'; }

  function fmtWindow(w) { return w ? `L${w.lapFrom}–${w.lapTo}` : '—'; }

  function windowClass(row) {
    if (row.isOverdue) return 'overdue';
    if (row.windowsDiverge) return 'diverge';
    return '';
  }
</script>

<div class="live-strategy">
  <div class="sub-tabs">
    <button class:active={liveTab === 'tracker'} on:click={() => liveTab = 'tracker'}>Strategy Tracker</button>
    <button class:active={liveTab === 'alerts'}  on:click={() => { liveTab = 'alerts'; if (sessionKey) loadAlerts(sessionKey); }}>
      Alerts
      {#if alertsData?.alerts?.length > 0}
        <span class="badge-count">{alertsData.alerts.length}</span>
      {/if}
    </button>
  </div>

  <!-- ── TRACKER ─────────────────────────────────────────────────────────── -->
  {#if liveTab === 'tracker'}
    {#if !sessionKey}
      <div class="state-msg">No active session.</div>
    {:else if trackerLoading && !trackerData}
      <div class="state-msg">Loading…</div>
    {:else if trackerError}
      <div class="state-msg error">{trackerError}</div>
    {:else if trackerData}
      <div class="tracker-meta">
        Lap {trackerData.currentLap ?? '—'} / {trackerData.totalLaps}
        <span class="refresh-hint">auto-refresh 15s</span>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Pos</th>
              <th>Driver</th>
              <th>Tyre</th>
              <th>Stint</th>
              <th>FP Window</th>
              <th>Real Window</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {#each trackerData.drivers.sort((a,b) => (a.position ?? 99) - (b.position ?? 99)) as row}
              <tr class:in-pit={row.inPit}>
                <td class="pos">P{row.position ?? '—'}</td>
                <td class="driver-cell"><strong>{row.driverCode}</strong></td>
                <td>
                  {#if row.compound}
                    <span class="compound-badge" style="background:{compoundBg(row.compound)};color:{compoundFg(row.compound)}">{row.compound}</span>
                  {:else}
                    <span class="dim">—</span>
                  {/if}
                </td>
                <td class="mono dim">{row.stintLaps != null ? `+${row.stintLaps}` : '—'}</td>
                <td class="mono">{fmtWindow(row.fpWindow)}</td>
                <td class="mono {windowClass(row)}">{fmtWindow(row.realWindow)}</td>
                <td>
                  {#if row.inPit}
                    <span class="status-badge pit">IN PIT</span>
                  {:else if row.isOverdue}
                    <span class="status-badge overdue">OVERDUE ⚠</span>
                  {:else}
                    <span class="status-badge on-track">ON TRACK</span>
                  {/if}
                </td>
              </tr>
            {/each}
          </tbody>
        </table>
      </div>
    {/if}

  <!-- ── ALERTS ──────────────────────────────────────────────────────────── -->
  {:else if liveTab === 'alerts'}
    {#if !sessionKey}
      <div class="state-msg">No active session.</div>
    {:else if alertsLoading && !alertsData}
      <div class="state-msg">Loading…</div>
    {:else if alertsError}
      <div class="state-msg error">{alertsError}</div>
    {:else if alertsData}
      {#if alertsData.alerts.length === 0}
        <div class="state-msg">No undercut/overcut alerts this session.</div>
      {:else}
        <div class="alerts-list">
          {#each [...alertsData.alerts].reverse() as alert}
            <div class="alert-card {alert.type.toLowerCase()}">
              <div class="alert-header">
                <span class="alert-type">{alert.type}</span>
                {#if alert.lap}<span class="alert-lap">Lap {alert.lap}</span>{/if}
              </div>
              <div class="alert-body">
                <span class="alert-driver">{alert.instigatorCode ?? alert.instigatorNumber}</span>
                <span class="alert-vs"> vs </span>
                <span class="alert-driver">{alert.rivalCode ?? alert.rivalNumber}</span>
                {#if alert.gapSeconds != null}
                  <span class="alert-gap">  gap {alert.gapSeconds.toFixed(1)}s</span>
                {/if}
              </div>
              {#if alert.predictedOutcome}
                <div class="alert-outcome">{alert.predictedOutcome}</div>
              {/if}
              {#if alert.confirmedOutcome}
                <div class="alert-confirmed">✓ {alert.confirmedOutcome}</div>
              {/if}
            </div>
          {/each}
        </div>
      {/if}
    {/if}
  {/if}
</div>

<style>
  .live-strategy { padding: 0.5rem; }

  .sub-tabs {
    display: flex;
    gap: 0.3rem;
    margin-bottom: 1rem;
    padding: 0.5rem;
  }

  .sub-tabs button {
    display: flex;
    align-items: center;
    gap: 0.4rem;
    padding: 0.3rem 0.85rem;
    font-size: 0.75rem;
    font-weight: 600;
    letter-spacing: 0.05em;
    background: transparent;
    color: var(--text-secondary);
    border: 1px solid #2a2a3a;
    border-radius: 4px;
    cursor: pointer;
    transition: all 0.15s;
  }

  .sub-tabs button:hover { color: var(--text-primary); border-color: #555; }
  .sub-tabs button.active { background: #E8002D; color: #fff; border-color: #E8002D; }

  .badge-count {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: #FF8000;
    color: #000;
    border-radius: 10px;
    font-size: 0.65rem;
    font-weight: 700;
    min-width: 1.1rem;
    height: 1.1rem;
    padding: 0 0.2rem;
  }

  .state-msg {
    padding: 3rem 2rem;
    color: var(--text-secondary);
    text-align: center;
  }
  .state-msg.error { color: #FF5252; }

  .tracker-meta {
    font-size: 0.78rem;
    color: var(--text-secondary);
    padding: 0 0.5rem 0.75rem;
    display: flex;
    justify-content: space-between;
  }

  .refresh-hint {
    font-size: 0.7rem;
    opacity: 0.5;
  }

  .table-wrap { overflow-x: auto; }

  table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.85rem;
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

  tr.in-pit { background: #0d0d14; color: var(--text-secondary); }
  tr:hover td { background: #1a1a2a; }

  .pos { font-weight: 700; }
  .driver-cell { font-weight: 600; }
  .mono { font-family: monospace; }
  .dim { color: var(--text-secondary); }

  .compound-badge {
    display: inline-block;
    padding: 0.1rem 0.4rem;
    border-radius: 3px;
    font-size: 0.72rem;
    font-weight: 700;
  }

  .overdue { color: #FF5252; font-weight: 700; }
  .diverge { color: #FFB74D; }

  .status-badge {
    display: inline-block;
    padding: 0.1rem 0.45rem;
    border-radius: 3px;
    font-size: 0.68rem;
    font-weight: 700;
    letter-spacing: 0.04em;
  }

  .status-badge.pit      { background: #0d1a2e; color: #64B5F6; border: 1px solid #1a3a6b; }
  .status-badge.overdue  { background: #2e0a0a; color: #FF5252; border: 1px solid #6b1a1a; }
  .status-badge.on-track { background: #0a1a0a; color: #69F0AE; border: 1px solid #1a4a1a; }

  /* Alerts */
  .alerts-list {
    display: flex;
    flex-direction: column;
    gap: 0.6rem;
    padding: 0.5rem;
  }

  .alert-card {
    background: var(--bg-secondary);
    border: 1px solid #2a2a3a;
    border-left: 3px solid #555;
    border-radius: 4px;
    padding: 0.6rem 0.85rem;
  }

  .alert-card.undercut { border-left-color: #FF8000; }
  .alert-card.overcut  { border-left-color: #E8002D; }

  .alert-header {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    margin-bottom: 0.3rem;
  }

  .alert-type {
    font-size: 0.68rem;
    font-weight: 700;
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }

  .alert-card.undercut .alert-type { color: #FF8000; }
  .alert-card.overcut  .alert-type { color: #E8002D; }

  .alert-lap {
    font-size: 0.72rem;
    color: var(--text-secondary);
    font-family: monospace;
  }

  .alert-body {
    font-size: 0.85rem;
  }

  .alert-driver { font-weight: 600; }
  .alert-vs     { color: var(--text-secondary); }
  .alert-gap    { font-family: monospace; font-size: 0.78rem; color: var(--text-secondary); }

  .alert-outcome {
    margin-top: 0.3rem;
    font-size: 0.78rem;
    color: var(--text-secondary);
  }

  .alert-confirmed {
    margin-top: 0.25rem;
    font-size: 0.78rem;
    color: #69F0AE;
  }

  @media (max-width: 599px) {
    .live-strategy { padding-bottom: 4rem; }
  }
</style>
