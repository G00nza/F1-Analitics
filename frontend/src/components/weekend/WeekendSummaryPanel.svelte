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
      const res = await fetch('/api/weekend/summary');
      if (!res.ok) throw new Error(res.statusText);
      data = await res.json();
    } catch (e) {
      error = 'Failed to load weekend summary';
    } finally {
      loading = false;
    }
  });

  function fmtLap(ms) {
    return ms != null ? formatLapTime(ms) : '—';
  }

  function fmtGap(ms) {
    if (ms == null || ms === 0) return 'leader';
    return `+${(ms / 1000).toFixed(3)}`;
  }
</script>

{#if loading}
  <div class="state-msg">Loading…</div>
{:else if error}
  <div class="state-msg error">{error}</div>
{:else if data}
  <div class="table-wrap">
    <table>
      <thead>
        <tr>
          <th rowspan="2" class="driver-col">Driver</th>
          {#each data.sessions as sessionType}
            <th colspan="3" class="session-group">{SESSION_LABELS[sessionType] ?? sessionType}</th>
          {/each}
        </tr>
        <tr>
          {#each data.sessions as _}
            <th class="sub">Pos</th>
            <th class="sub">Best Lap</th>
            <th class="sub">Gap</th>
          {/each}
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
              {@const entry = driver.sessionData[sessionType]}
              {#if entry}
                <td class="pos" class:best={entry.isBestPosition}>{entry.position ? `P${entry.position}` : '—'}</td>
                <td class="mono">{fmtLap(entry.bestLapMs)}</td>
                <td class="gap mono">{fmtGap(entry.gapToLeaderMs)}</td>
              {:else}
                <td class="dim">—</td><td class="dim">—</td><td class="dim">—</td>
              {/if}
            {/each}
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

  .table-wrap { overflow-x: auto; }

  table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.83rem;
  }

  th, td {
    padding: 0.45rem 0.7rem;
    text-align: left;
    border-bottom: 1px solid #1e1e2e;
    white-space: nowrap;
  }

  th {
    color: var(--text-secondary);
    font-size: 0.7rem;
    font-weight: 600;
    letter-spacing: 0.07em;
    text-transform: uppercase;
  }

  .driver-col { min-width: 75px; }

  .session-group {
    text-align: center;
    border-left: 2px solid #2a2a3a;
    color: var(--text-primary);
    font-size: 0.75rem;
  }

  .sub { border-left: 1px solid #1e1e2e; }
  .sub:first-of-type { border-left: 2px solid #2a2a3a; }

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

  .pos { font-weight: 600; }
  .best { color: #00C853; }

  .gap { color: var(--text-secondary); font-size: 0.78rem; }

  .mono { font-family: monospace; }
  .dim  { color: var(--text-secondary); }

  tr:hover td { background: #1a1a2a; }
</style>
