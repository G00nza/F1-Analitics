<script>
  import { flip } from 'svelte/animate';
  import { sessionState } from '../stores/session.js';
  import TyreIndicator from './TyreIndicator.svelte';
  import { formatLapTime } from '../lib/f1utils.js';

  $: ts = $sessionState?.trackStatus;
  $: isSC      = ts === '4';
  $: isVSC     = ts === '6' || ts === '7';
  $: isRedFlag = ts === '5';
</script>

{#if $sessionState?.drivers}
  {#if isRedFlag}
    <div class="track-banner red-flag">🔴 RED FLAG — SESSION SUSPENDED</div>
  {:else if isSC}
    <div class="track-banner safety-car">🚗 SAFETY CAR DEPLOYED</div>
  {:else if isVSC}
    <div class="track-banner safety-car">🚗 VIRTUAL SAFETY CAR</div>
  {/if}
  <!-- Desktop + Tablet: table view -->
  <div class="leaderboard table-view">
    <table>
      <thead>
        <tr>
          <th>P</th>
          <th>#</th>
          <th>Driver</th>
          <th>Team</th>
          <th>Best Lap</th>
          <th>Gap</th>
          <th class="sector-col">S1</th>
          <th class="sector-col">S2</th>
          <th class="sector-col">S3</th>
          <th>Tyre</th>
        </tr>
      </thead>
      <tbody>
        {#each $sessionState.drivers as driver (driver.driverNumber)}
          <tr animate:flip={{ duration: 300 }} class:in-pit={driver.inPit}>
            <td class="pos">{driver.position}</td>
            <td class="num">{driver.driverNumber}</td>
            <td class="code">{driver.driverCode}</td>
            <td class="team">{driver.teamName}</td>
            <td class="laptime">{formatLapTime(driver.bestLapMs)}</td>
            <td class="gap">{driver.gapToLeader ?? 'leader'}</td>
            <td class="sector sector-col">{driver.lastS1 != null ? formatLapTime(driver.lastS1) : ''}</td>
            <td class="sector sector-col">{driver.lastS2 != null ? formatLapTime(driver.lastS2) : ''}</td>
            <td class="sector sector-col">{driver.lastS3 != null ? formatLapTime(driver.lastS3) : ''}</td>
            <td><TyreIndicator compound={driver.currentCompound} laps={driver.currentStintLaps} /></td>
          </tr>
        {/each}
      </tbody>
    </table>
  </div>

  <!-- Mobile: card view -->
  <div class="leaderboard card-view">
    {#each $sessionState.drivers as driver (driver.driverNumber)}
      <div class="driver-card" class:in-pit={driver.inPit} animate:flip={{ duration: 300 }}>
        <div class="card-pos-num">
          <span class="card-pos">P{driver.position}</span>
          <span class="card-num">{driver.driverNumber}</span>
        </div>
        <div class="card-info">
          <div class="card-name-team">
            <span class="card-code">{driver.driverCode}</span>
            <span class="card-team">{driver.teamName}</span>
          </div>
          <div class="card-times">
            <span class="card-laptime">{formatLapTime(driver.bestLapMs)}</span>
            <span class="card-gap">{driver.gapToLeader ?? 'leader'}</span>
          </div>
        </div>
        <div class="card-tyre">
          <TyreIndicator compound={driver.currentCompound} laps={driver.currentStintLaps} />
        </div>
      </div>
    {/each}
  </div>
{/if}

<style>
  .leaderboard {
    flex: 1;
    overflow-x: auto;
    padding: 0.5rem;
  }

  /* ── Track status banners ── */
  .track-banner {
    padding: 0.5rem 1rem;
    font-weight: bold;
    font-size: 0.9rem;
    text-align: center;
    letter-spacing: 0.06em;
  }

  .red-flag {
    background-color: #7a0000;
    color: #fff;
    border-bottom: 2px solid #FF1E00;
  }

  .safety-car {
    background-color: #5a3800;
    color: #FFF200;
    border-bottom: 2px solid #FF8000;
  }

  /* ── Table view: desktop + tablet ── */
  .table-view { display: block; }
  .card-view  { display: none; }

  table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.875rem;
  }

  thead th {
    padding: 0.5rem 0.75rem;
    text-align: left;
    color: var(--text-secondary);
    font-size: 0.75rem;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    border-bottom: 1px solid #333;
  }

  tbody tr {
    border-bottom: 1px solid #222;
    transition: background-color 0.3s;
  }

  tbody tr:hover { background-color: #1a1a2a; }

  tbody tr.in-pit {
    background-color: #0d0d14;
    color: var(--text-secondary);
  }

  td {
    padding: 0.5rem 0.75rem;
    white-space: nowrap;
  }

  .pos    { font-weight: bold; }
  .num    { color: var(--text-secondary); font-size: 0.8rem; }
  .code   { font-weight: bold; letter-spacing: 0.05em; }
  .team   { color: var(--text-secondary); font-size: 0.8rem; }
  .laptime { font-family: monospace; }
  .gap    { color: var(--text-secondary); font-family: monospace; }
  .sector { font-family: monospace; font-size: 0.8rem; color: var(--text-secondary); }

  /* Tablet: hide sector columns */
  @media (max-width: 1023px) {
    .sector-col { display: none; }
  }

  /* Mobile: switch from table to cards */
  @media (max-width: 599px) {
    .table-view { display: none; }
    .card-view  { display: flex; flex-direction: column; gap: 0.5rem; padding: 0.5rem; }
  }

  /* ── Card styles (mobile) ── */
  .driver-card {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    background-color: var(--bg-secondary);
    border-radius: 6px;
    padding: 0.6rem 0.75rem;
    border: 1px solid #333;
    transition: background-color 0.3s;
  }

  .driver-card.in-pit {
    background-color: #0d0d14;
    color: var(--text-secondary);
  }

  .card-pos-num {
    display: flex;
    flex-direction: column;
    align-items: center;
    min-width: 2.5rem;
  }

  .card-pos {
    font-weight: bold;
    font-size: 1rem;
  }

  .card-num {
    font-size: 0.75rem;
    color: var(--text-secondary);
  }

  .card-info {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 0.2rem;
  }

  .card-name-team {
    display: flex;
    gap: 0.5rem;
    align-items: baseline;
  }

  .card-code {
    font-weight: bold;
    letter-spacing: 0.05em;
    font-size: 0.95rem;
  }

  .card-team {
    font-size: 0.75rem;
    color: var(--text-secondary);
  }

  .card-times {
    display: flex;
    gap: 0.75rem;
  }

  .card-laptime {
    font-family: monospace;
    font-size: 0.85rem;
  }

  .card-gap {
    font-family: monospace;
    font-size: 0.85rem;
    color: var(--text-secondary);
  }

  .card-tyre {
    min-width: 2rem;
    text-align: right;
  }
</style>
