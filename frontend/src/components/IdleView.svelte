<script>
  import { connectionStatus, nextSession } from '../stores/session.js';
</script>

<div class="idle">
  <h1>F1 Analytics</h1>
  <p class="message">No active session right now.</p>

  {#if $nextSession}
    <div class="next-session">
      <p class="label">Next session</p>
      <p class="name">{$nextSession.sessionName}</p>
    </div>
  {/if}

  <p class="status">
    {#if $connectionStatus === 'connected'}
      Connected — waiting for next session to start.
    {:else if $connectionStatus === 'reconnecting'}
      Reconnecting...
    {:else}
      Connecting...
    {/if}
  </p>
</div>

<style>
  .idle {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 1rem;
    text-align: center;
    padding: 2rem;
  }

  h1 {
    font-size: 2rem;
    letter-spacing: 0.1em;
  }

  .message {
    font-size: 1.1rem;
    color: var(--text-secondary);
  }

  .next-session {
    background-color: var(--bg-secondary);
    border: 1px solid #333;
    border-radius: 4px;
    padding: 0.75rem 1.5rem;
  }

  .label {
    font-size: 0.75rem;
    text-transform: uppercase;
    letter-spacing: 0.1em;
    color: var(--text-secondary);
    margin-bottom: 0.25rem;
  }

  .name {
    font-size: 1rem;
    font-weight: bold;
  }

  .status {
    font-size: 0.875rem;
    color: var(--text-secondary);
  }
</style>
