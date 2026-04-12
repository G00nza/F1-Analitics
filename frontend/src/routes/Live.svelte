<script>
  import Leaderboard from '../components/Leaderboard.svelte';
  import RaceControlBar from '../components/RaceControlBar.svelte';
  import IdleView from '../components/IdleView.svelte';
  import LiveStrategyPanel from '../components/live/LiveStrategyPanel.svelte';
  import { sessionState } from '../stores/session.js';

  let liveView = 'leaderboard';
</script>

{#if $sessionState}
  <div class="live-tabs">
    <button class:active={liveView === 'leaderboard'} on:click={() => liveView = 'leaderboard'}>Leaderboard</button>
    <button class:active={liveView === 'strategy'}    on:click={() => liveView = 'strategy'}>Strategy</button>
  </div>

  {#if liveView === 'leaderboard'}
    <Leaderboard />
    <RaceControlBar />
  {:else}
    <LiveStrategyPanel />
  {/if}
{:else}
  <IdleView />
{/if}

<style>
  .live-tabs {
    display: flex;
    gap: 0;
    background: var(--bg-secondary);
    border-bottom: 1px solid #2a2a3a;
    padding: 0 0.5rem;
  }

  .live-tabs button {
    padding: 0.5rem 1.1rem;
    font-size: 0.78rem;
    font-weight: 600;
    letter-spacing: 0.06em;
    text-transform: uppercase;
    background: transparent;
    color: var(--text-secondary);
    border: none;
    border-bottom: 2px solid transparent;
    cursor: pointer;
    transition: color 0.15s, border-color 0.15s;
  }

  .live-tabs button:hover { color: var(--text-primary); }

  .live-tabs button.active {
    color: var(--text-primary);
    border-bottom-color: #E8002D;
  }
</style>
