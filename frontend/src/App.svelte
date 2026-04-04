<script>
  import { onMount } from 'svelte';
  import Header from './components/Header.svelte';
  import NavBar from './components/NavBar.svelte';
  import Live from './routes/Live.svelte';
  import Weekend from './routes/Weekend.svelte';
  import Season from './routes/Season.svelte';

  let route = getRoute();

  function getRoute() {
    const hash = window.location.hash.slice(1) || '/live';
    return hash.startsWith('/') ? hash : '/' + hash;
  }

  onMount(() => {
    const onHashChange = () => { route = getRoute(); };
    window.addEventListener('hashchange', onHashChange);
    return () => window.removeEventListener('hashchange', onHashChange);
  });
</script>

<main>
  <Header />
  <NavBar {route} />
  <div class="view">
    {#if route === '/live' || route === '/'}
      <Live />
    {:else if route === '/weekend'}
      <Weekend />
    {:else if route === '/season'}
      <Season />
    {/if}
  </div>
</main>

<style>
  :global(*) {
    box-sizing: border-box;
    margin: 0;
    padding: 0;
  }

  :global(:root) {
    --color-best-sector:   #BF5FFF;
    --color-personal-best: #00C853;
    --color-normal:        #FFFFFF;
    --color-slow:          #FF5252;

    --tyre-soft:   #FF1E00;
    --tyre-medium: #FFF200;
    --tyre-hard:   #EBEBEB;
    --tyre-inter:  #39B54A;
    --tyre-wet:    #0067FF;

    --bg-primary:    #15151E;
    --bg-secondary:  #1F1F2E;
    --text-primary:  #FFFFFF;
    --text-secondary:#AAAAAA;
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

  .view {
    flex: 1;
    display: flex;
    flex-direction: column;
  }
</style>
