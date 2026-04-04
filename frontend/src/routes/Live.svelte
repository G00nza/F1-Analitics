<script>
  import Leaderboard from '../components/Leaderboard.svelte';
  import RaceControlBar from '../components/RaceControlBar.svelte';
  import IdleView from '../components/IdleView.svelte';
  import { sessionState } from '../stores/session.js';
</script>

{#if $sessionState}
  <Leaderboard />
  <RaceControlBar />
{:else}
  <IdleView />
{/if}
