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

  // Default to FP2 if available, otherwise first FP session
  $: if (fpSessions.length && !selectedKey) {
    const fp2 = fpSessions.find(s => s.type === 'FP2');
    selectedKey = fp2 ? fp2.key : fpSessions[0].key;
  }

  $: if (selectedKey && selectedKey !== lastLoadedKey) {
    lastLoadedKey = selectedKey;
    loadData(selectedKey);
  }

  async function loadData(key) {
    loading = true; error = null; data = null;
    try {
      const res = await fetch(`/api/sessions/${key}/race-pace`);
      if (!res.ok) throw new Error(res.statusText);
      data = await res.json();
    } catch (e) {
      error = 'Failed to load race pace data';
    } finally {
      loading = false;
    }
  }

  function fmtLap(ms) {
    return ms != null ? formatLapTime(ms) : '—';
  }

  function fmtGap(ms) {
    if (ms == null || ms === 0) return '—';
    return `+${(ms / 1000).toFixed(3)}`;
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
    {#if data.warning}
      <div class="warning">{data.warning}</div>
    {/if}
    {#if !data.hasStintData}
      <div class="warning">Stint data not available — pace estimates may be inaccurate.</div>
    {/if}

    {#if data.teams.length === 0}
      <div class="state-msg">No long-run pace data found for this session.</div>
    {:else}
      <h4 class="section-title">Estimated Race Pace — Based on Long Runs</h4>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Rank</th>
              <th>Team</th>
              <th>Avg Lap (adj)</th>
              <th>Gap to Best</th>
            </tr>
          </thead>
          <tbody>
            {#each data.teams as row}
              <tr>
                <td class="rank">{row.rank}</td>
                <td class="team-cell">
                  <span class="dot" style="background:{teamColor(row.team)}"></span>
                  {row.team}
                </td>
                <td class="mono">{fmtLap(row.avgLapMs)}</td>
                <td class="mono gap">{fmtGap(row.gapToLeaderMs)}</td>
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
    max-width: 580px;
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

  .rank {
    color: var(--text-secondary);
    font-weight: 600;
    width: 40px;
  }

  td.rank:first-child { color: var(--text-primary); }

  .team-cell {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    font-weight: 600;
  }

  .dot {
    display: inline-block;
    width: 10px;
    height: 10px;
    border-radius: 50%;
    flex-shrink: 0;
  }

  .mono { font-family: monospace; }

  .gap { color: var(--text-secondary); }

  tr:first-child .mono { color: #00C853; font-weight: 600; }

  tr:hover td { background: #1a1a2a; }
</style>
