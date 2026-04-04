<script>
  import Header from './components/Header.svelte';
  import Leaderboard from './components/Leaderboard.svelte';
  import RaceControlBar from './components/RaceControlBar.svelte';
  import IdleView from './components/IdleView.svelte';
  import { sessionState, connectionStatus } from './stores/session.js';
</script>

<main>
  <Header />
  {#if $sessionState}
    <Leaderboard />
    <RaceControlBar />
  {:else}
    <IdleView />
  {/if}
</main>

<style>
  :global(*) {
    box-sizing: border-box;
    margin: 0;
    padding: 0;
  }

  :global(:root) {
    --color-best-sector: #BF5FFF;
    --color-personal-best: #00C853;
    --color-normal: #FFFFFF;
    --color-slow: #FF5252;

    --tyre-soft: #FF1E00;
    --tyre-medium: #FFF200;
    --tyre-hard: #EBEBEB;
    --tyre-inter: #39B54A;
    --tyre-wet: #0067FF;

    --bg-primary: #15151E;
    --bg-secondary: #1F1F2E;
    --text-primary: #FFFFFF;
    --text-secondary: #AAAAAA;
  }

  :global(body) {
    background-color: var(--bg-primary);
    color: var(--text-primary);
    font-family: 'Arial', sans-serif;
    min-height: 100vh;
  }

  main {
    display: flex;
    flex-direction: column;
    min-height: 100vh;
  }
</style>
