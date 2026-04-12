<script>
  import { formatLapTime, teamColor } from '../../lib/f1utils.js';

  export let sessions = [];

  const SESSION_LABELS = { FP1: 'FP1', FP2: 'FP2', FP3: 'FP3' };

  $: fpSessions = sessions.filter(s => s.type.startsWith('FP'));

  let selectedKey = null;
  let lastLoadedKey = null;
  let data = null;
  let loading = false;
  let error = null;

  // Auto-select first FP session when the list arrives
  $: if (fpSessions.length && !selectedKey) {
    selectedKey = fpSessions[0].key;
  }

  // Fetch when selected key changes
  $: if (selectedKey && selectedKey !== lastLoadedKey) {
    lastLoadedKey = selectedKey;
    loadData(selectedKey);
  }

  async function loadData(key) {
    loading = true; error = null; data = null;
    try {
      const res = await fetch(`/api/sessions/${key}/tyre-degradation`);
      if (!res.ok) throw new Error(res.statusText);
      data = await res.json();
    } catch (e) {
      error = 'Failed to load tyre degradation data';
    } finally {
      loading = false;
    }
  }

  function fmtLap(ms) {
    return ms != null ? formatLapTime(ms) : '—';
  }

  function fmtDegRate(ms) {
    if (ms == null) return '—';
    const sign = ms >= 0 ? '+' : '−';
    return `${sign}${(Math.abs(ms) / 1000).toFixed(3)}s/lap`;
  }

  function degClass(ms) {
    if (ms == null) return '';
    if (ms > 100) return 'deg-high';
    if (ms > 50)  return 'deg-med';
    return 'deg-low';
  }
</script>

{#if fpSessions.length === 0}
  <div class="state-msg">No free practice sessions available.</div>
{:else}
  <div class="fp-tabs">
    {#each fpSessions as s}
      <button class:active={s.key === selectedKey} on:click={() => selectedKey = s.key}>
        {SESSION_LABELS[s.type] ?? s.name}
      </button>
    {/each}
  </div>

  {#if loading}
    <div class="state-msg">Loading…</div>
  {:else if error}
    <div class="state-msg error">{error}</div>
  {:else if data}
    {#if !data.hasStintData}
      <div class="warning">Stint data not available for this session — degradation analysis may be incomplete.</div>
    {/if}

    {#if data.longRuns.length === 0}
      <div class="state-msg">No long runs (5+ laps) found in this session.</div>
    {:else}
      <h4 class="section-title">Long Runs — Race Simulation</h4>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Driver</th>
              <th>Compound</th>
              <th>Laps</th>
              <th>First Lap</th>
              <th>Last Lap</th>
              <th>Avg Lap</th>
              <th>Deg/Lap</th>
            </tr>
          </thead>
          <tbody>
            {#each data.longRuns as run}
              <tr>
                <td class="driver-cell">
                  <span class="dot" style="background:{teamColor(run.team)}"></span>
                  {run.driverCode}
                </td>
                <td>
                  <span class="compound {(run.compound ?? '').toLowerCase()}">
                    {run.compound ?? '?'}
                  </span>
                </td>
                <td class="mono">{run.lapCount}</td>
                <td class="mono">{fmtLap(run.firstLapMs)}</td>
                <td class="mono">{fmtLap(run.lastLapMs)}</td>
                <td class="mono">{fmtLap(run.avgLapMs)}</td>
                <td class="mono {degClass(run.degPerLapMs)}">{fmtDegRate(run.degPerLapMs)}</td>
              </tr>
            {/each}
          </tbody>
        </table>
      </div>
    {/if}
  {/if}
{/if}

<style>
  .state-msg {
    padding: 3rem 2rem;
    color: var(--text-secondary);
    text-align: center;
  }
  .state-msg.error { color: #FF5252; }

  .warning {
    background: #1e1a0e;
    border: 1px solid #6b5010;
    color: #c89020;
    padding: 0.6rem 1rem;
    border-radius: 4px;
    font-size: 0.8rem;
    margin-bottom: 1.25rem;
  }

  .fp-tabs {
    display: flex;
    gap: 0.4rem;
    margin-bottom: 1.5rem;
  }

  .fp-tabs button {
    padding: 0.3rem 0.9rem;
    font-size: 0.78rem;
    font-weight: 600;
    letter-spacing: 0.05em;
    background: var(--bg-secondary);
    color: var(--text-secondary);
    border: 1px solid #333;
    border-radius: 4px;
    cursor: pointer;
    transition: all 0.15s;
  }

  .fp-tabs button:hover,
  .fp-tabs button.active {
    background: #E8002D;
    color: #fff;
    border-color: #E8002D;
  }

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

  .driver-cell {
    display: flex;
    align-items: center;
    gap: 0.4rem;
    font-weight: 600;
  }

  .dot {
    display: inline-block;
    width: 8px;
    height: 8px;
    border-radius: 50%;
    flex-shrink: 0;
  }

  .mono { font-family: monospace; }

  .compound {
    display: inline-block;
    padding: 0.1rem 0.4rem;
    border-radius: 3px;
    font-size: 0.72rem;
    font-weight: bold;
    text-transform: uppercase;
  }
  .compound.soft     { background: var(--tyre-soft);   color: #111; }
  .compound.medium   { background: var(--tyre-medium);  color: #111; }
  .compound.hard     { background: var(--tyre-hard);    color: #111; }
  .compound.inter    { background: var(--tyre-inter);   color: #111; }
  .compound.wet      { background: var(--tyre-wet);     color: #fff; }

  .deg-high { color: #FF5252; font-weight: 600; }
  .deg-med  { color: #FFB74D; }
  .deg-low  { color: #69F0AE; }

  tr:hover td { background: #1a1a2a; }
</style>
