<script>
  import { sessionState } from '../stores/session.js';
  export let route = '/live';

  $: isLive = $sessionState?.sessionStatus === 'ACTIVE';
</script>

<nav>
  <a href="#/live" class:active={route === '/live' || route === '/'}>
    Live
    {#if isLive}<span class="live-dot"></span>{/if}
  </a>
  <a href="#/weekend" class:active={route === '/weekend'}>Weekend</a>
  <a href="#/season" class:active={route === '/season'}>Season</a>
</nav>

<style>
  nav {
    display: flex;
    background: var(--bg-secondary);
    border-bottom: 1px solid #2a2a3a;
    padding: 0 1rem;
  }

  a {
    display: flex;
    align-items: center;
    gap: 0.4rem;
    padding: 0.6rem 1.1rem;
    color: var(--text-secondary);
    text-decoration: none;
    font-size: 0.8rem;
    font-weight: 600;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    border-bottom: 2px solid transparent;
    transition: color 0.15s, border-color 0.15s;
  }

  a:hover { color: var(--text-primary); }

  a.active {
    color: var(--text-primary);
    border-bottom-color: #E8002D;
  }

  .live-dot {
    display: inline-block;
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: #00C853;
    animation: blink 1s step-start infinite;
  }

  @keyframes blink {
    0%, 100% { opacity: 1; }
    50%       { opacity: 0; }
  }

  /* Mobile: fixed bottom bar */
  @media (max-width: 599px) {
    nav {
      position: fixed;
      bottom: 0;
      left: 0;
      right: 0;
      z-index: 100;
      border-top: 1px solid #2a2a3a;
      border-bottom: none;
      padding: 0;
      justify-content: space-around;
    }

    a {
      flex: 1;
      justify-content: center;
      padding: 0.8rem 0.5rem;
      font-size: 0.72rem;
    }
  }
</style>
