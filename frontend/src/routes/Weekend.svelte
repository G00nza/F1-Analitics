<script>
  import { onMount } from 'svelte';
  import LapTimeChart from '../components/charts/LapTimeChart.svelte';
  import DegradationChart from '../components/charts/DegradationChart.svelte';
  import PositionChart from '../components/charts/PositionChart.svelte';
  import GapChart from '../components/charts/GapChart.svelte';

  const CHART_COMPONENTS = {
    lapTimes: LapTimeChart,
    positions: PositionChart,
    gap: GapChart,
    degradation: DegradationChart,
  };

  const SESSION_LABELS = {
    FP1: 'Practice 1', FP2: 'Practice 2', FP3: 'Practice 3',
    QUALIFYING: 'Qualifying', RACE: 'Race',
    SPRINT: 'Sprint', SPRINT_QUALIFYING: 'Sprint Qualifying',
  };

  let weekend = null;
  let sessions = [];
  let selectedKey = null;
  let chartsData = null;
  let loading = false;
  let error = null;

  onMount(async () => {
    try {
      const res = await fetch('/api/weekend');
      if (!res.ok) throw new Error(res.statusText);
      weekend = await res.json();
      sessions = weekend.sessions ?? [];
      if (sessions.length) {
        selectedKey = sessions[sessions.length - 1].key;
        await loadSession(selectedKey);
      }
    } catch (e) {
      error = 'Failed to load weekend info';
    }
  });

  async function loadSession(key) {
    loading = true;
    error = null;
    chartsData = null;
    try {
      const res = await fetch(`/api/sessions/${key}/charts`);
      if (!res.ok) throw new Error(res.statusText);
      chartsData = await res.json();
    } catch (e) {
      error = 'Failed to load session data';
    } finally {
      loading = false;
    }
  }

  async function selectSession(key) {
    if (key === selectedKey) return;
    selectedKey = key;
    await loadSession(key);
  }

  let sortCol = 'lapTimeMs';
  let sortAsc = true;

  function sortBy(col) {
    if (sortCol === col) { sortAsc = !sortAsc; }
    else { sortCol = col; sortAsc = true; }
  }

  $: sortedBestLaps = chartsData
    ? [...chartsData.bestLaps].sort((a, b) => {
        const av = a[sortCol] ?? (sortAsc ? Infinity : -Infinity);
        const bv = b[sortCol] ?? (sortAsc ? Infinity : -Infinity);
        const cmp = typeof av === 'string' ? av.localeCompare(bv) : av - bv;
        return sortAsc ? cmp : -cmp;
      })
    : [];

  function fmtLap(ms) {
    if (!ms) return '--:--.---';
    const m = Math.floor(ms / 60000);
    const s = Math.floor((ms % 60000) / 1000);
    const f = ms % 1000;
    return `${m}:${String(s).padStart(2, '0')}.${String(f).padStart(3, '0')}`;
  }
</script>

<div class="weekend">
  {#if weekend}
    <div class="meeting-header">
      <h2>{weekend.meetingName} {weekend.year}</h2>
      <span class="circuit">{weekend.circuitName}</span>
    </div>
  {/if}

  <!-- Session tabs -->
  <div class="session-tabs">
    {#each sessions as s}
      <button class:active={s.key === selectedKey} on:click={() => selectSession(s.key)}>
        {SESSION_LABELS[s.type] ?? s.name}
      </button>
    {/each}
  </div>

  {#if loading}
    <div class="state-msg">Loading…</div>
  {:else if error}
    <div class="state-msg error">{error}</div>
  {:else if chartsData}

    <!-- Best lap times table -->
    <section class="section">
      <h3 class="section-title">Best Lap Times</h3>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Pos</th>
              <th class="sortable" on:click={() => sortBy('driverCode')}>
                Driver {sortCol === 'driverCode' ? (sortAsc ? '↑' : '↓') : ''}
              </th>
              <th class="sortable active-sort" on:click={() => sortBy('lapTimeMs')}>
                Best Lap {sortCol === 'lapTimeMs' ? (sortAsc ? '↑' : '↓') : ''}
              </th>
              <th>Compound</th>
              <th class="sortable" on:click={() => sortBy('lapNumber')}>
                Lap # {sortCol === 'lapNumber' ? (sortAsc ? '↑' : '↓') : ''}
              </th>
            </tr>
          </thead>
          <tbody>
            {#each sortedBestLaps as lap, i}
              <tr>
                <td class="dim">{i + 1}</td>
                <td>
                  <span class="driver-dot" style="background:{lap.teamColor}"></span>
                  {lap.driverCode}
                </td>
                <td class="mono">{fmtLap(lap.lapTimeMs)}</td>
                <td>
                  <span class="compound {(lap.compound ?? '').toLowerCase()}">
                    {lap.compound ?? '--'}
                  </span>
                </td>
                <td class="mono dim">{lap.lapNumber}</td>
              </tr>
            {/each}
          </tbody>
        </table>
      </div>
    </section>

    <!-- Charts driven by backend -->
    {#each chartsData.charts as chart}
      <section class="section">
        <h3 class="section-title">{chart.title}</h3>
        <svelte:component this={CHART_COMPONENTS[chart.type]} datasets={chart.datasets} />
      </section>
    {/each}

  {:else}
    <div class="state-msg">No data available for this session.</div>
  {/if}
</div>

<style>
  .weekend {
    padding: 1.25rem 1.5rem;
    max-width: 1100px;
    margin: 0 auto;
    width: 100%;
  }

  .meeting-header {
    margin-bottom: 1rem;
  }

  h2 {
    font-size: 1.25rem;
    font-weight: bold;
    color: var(--text-primary);
    margin: 0 0 0.2rem;
  }

  .circuit {
    font-size: 0.82rem;
    color: var(--text-secondary);
  }

  /* Session tabs */
  .session-tabs {
    display: flex;
    flex-wrap: wrap;
    gap: 0.4rem;
    margin-bottom: 1.5rem;
  }

  .session-tabs button {
    padding: 0.35rem 0.85rem;
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

  .session-tabs button:hover,
  .session-tabs button.active {
    background: #E8002D;
    color: #fff;
    border-color: #E8002D;
  }

  /* State messages */
  .state-msg {
    padding: 3rem 2rem;
    color: var(--text-secondary);
    text-align: center;
  }

  .state-msg.error { color: #FF5252; }

  /* Sections */
  .section { margin-bottom: 2.5rem; }

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

  /* Best lap table */
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
  }

  th {
    color: var(--text-secondary);
    font-size: 0.72rem;
    font-weight: 600;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    white-space: nowrap;
  }

  th.sortable { cursor: pointer; user-select: none; }
  th.sortable:hover { color: var(--text-primary); }
  th.active-sort { color: #E8002D; }

  td.dim { color: var(--text-secondary); }

  .driver-dot {
    display: inline-block;
    width: 8px;
    height: 8px;
    border-radius: 50%;
    margin-right: 0.4rem;
    vertical-align: middle;
    flex-shrink: 0;
  }

  .mono { font-family: monospace; }

  .compound {
    display: inline-block;
    padding: 0.1rem 0.4rem;
    border-radius: 3px;
    font-size: 0.72rem;
    font-weight: bold;
  }

  .compound.soft   { background: var(--tyre-soft);   color: #111; }
  .compound.medium { background: var(--tyre-medium);  color: #111; }
  .compound.hard   { background: var(--tyre-hard);    color: #111; }
  .compound.inter  { background: var(--tyre-inter);   color: #111; }
  .compound.wet    { background: var(--tyre-wet);     color: #fff; }

  /* Mobile: bottom nav padding */
  @media (max-width: 599px) {
    .weekend { padding: 1rem 1rem 5rem; }
  }
</style>
