<script>
  import { onMount } from 'svelte';
  import { formatLapTime, teamColor } from '../../lib/f1utils.js';

  const SESSION_LABELS = {
    FP1: 'FP1', FP2: 'FP2', FP3: 'FP3',
    QUALIFYING: 'Q', RACE: 'R', SPRINT: 'Sprint', SPRINT_QUALIFYING: 'SQ',
  };

  let data = null;
  let loading = true;
  let error = null;

  onMount(async () => {
    try {
      const res = await fetch('/api/weekend/progression');
      if (!res.ok) throw new Error(res.statusText);
      data = await res.json();
    } catch (e) {
      error = 'Failed to load lap time progression';
    } finally {
      loading = false;
    }
  });

  function fmtLap(ms) {
    return ms != null ? formatLapTime(ms) : '—';
  }

  function fmtDelta(ms) {
    if (ms == null) return '—';
    const abs = Math.abs(ms);
    const s = Math.floor(abs / 1000);
    const f = String(abs % 1000).padStart(3, '0');
    const sign = ms < 0 ? '−' : '+';
    return `${sign}${s}.${f}s`;
  }

  function deltaClass(ms) {
    if (ms == null) return 'dim';
    if (ms < -2500) return 'delta-great';
    if (ms < -1000) return 'delta-good';
    if (ms > 0) return 'delta-bad';
    return '';
  }
</script>

{#if loading}
  <div class="state-msg">Loading…</div>
{:else if error}
  <div class="state-msg error">{error}</div>
{:else if data}
  {#if data.fpDataWarning}
    <div class="warning">{data.fpDataWarning}</div>
  {/if}
  <div class="table-wrap">
    <table>
      <thead>
        <tr>
          <th>Driver</th>
          {#each data.sessions as sessionType}
            <th>{SESSION_LABELS[sessionType] ?? sessionType}</th>
          {/each}
          <th>Δ FP1→Q</th>
        </tr>
      </thead>
      <tbody>
        {#each data.drivers as driver}
          <tr>
            <td class="driver-cell">
              <span class="dot" style="background:{teamColor(driver.team)}"></span>
              {driver.driverCode}
            </td>
            {#each data.sessions as sessionType}
              <td class="mono">{fmtLap(driver.lapTimes[sessionType])}</td>
            {/each}
            <td class="mono {deltaClass(driver.deltaFp1ToQualiMs)}">{fmtDelta(driver.deltaFp1ToQualiMs)}</td>
          </tr>
        {/each}
      </tbody>
    </table>
  </div>
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
  .dim  { color: var(--text-secondary); }

  .delta-great { color: #00C853; font-weight: 700; }
  .delta-good  { color: #69F0AE; }
  .delta-bad   { color: #FF5252; }

  tr:hover td { background: #1a1a2a; }
</style>
