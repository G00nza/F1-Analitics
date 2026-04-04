<script>
  import TyreIndicator from './TyreIndicator.svelte';
  import { formatLapTime } from '../lib/f1utils.js';

  export let driver;
</script>

<tr class:in-pit={driver.inPit}>
  <td class="pos">{driver.position}</td>
  <td class="num">{driver.driverNumber}</td>
  <td class="code">{driver.driverCode}</td>
  <td class="team">{driver.teamName}</td>
  <td class="laptime">{formatLapTime(driver.bestLapMs)}</td>
  <td class="gap">{driver.gapToLeader ?? 'leader'}</td>
  <td class="sector">{driver.lastS1 != null ? formatLapTime(driver.lastS1) : ''}</td>
  <td class="sector">{driver.lastS2 != null ? formatLapTime(driver.lastS2) : ''}</td>
  <td class="sector">{driver.lastS3 != null ? formatLapTime(driver.lastS3) : ''}</td>
  <td><TyreIndicator compound={driver.currentCompound} laps={driver.currentStintLaps} /></td>
</tr>

<style>
  tr {
    border-bottom: 1px solid #222;
    transition: background-color 0.3s;
  }

  tr:hover {
    background-color: #1a1a2a;
  }

  tr.in-pit {
    background-color: #0d0d14;
    color: var(--text-secondary);
  }

  td {
    padding: 0.5rem 0.75rem;
    white-space: nowrap;
  }

  .pos {
    font-weight: bold;
    color: var(--text-primary);
  }

  .num {
    color: var(--text-secondary);
    font-size: 0.8rem;
  }

  .code {
    font-weight: bold;
    letter-spacing: 0.05em;
  }

  .team {
    color: var(--text-secondary);
    font-size: 0.8rem;
  }

  .laptime {
    font-family: monospace;
  }

  .gap {
    color: var(--text-secondary);
    font-family: monospace;
  }

  .sector {
    font-family: monospace;
    font-size: 0.8rem;
    color: var(--text-secondary);
  }

  @media (max-width: 768px) {
    .sector { display: none; }
  }
</style>
