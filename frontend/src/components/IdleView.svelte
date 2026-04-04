<script>
  import { onMount, onDestroy } from 'svelte';
  import { connectionStatus, nextSession, connectToReplay } from '../stores/session.js';

  let latestSession = null;  // { key, name, type, status, recorded }
  let countdown = null;      // seconds remaining until next session
  let countdownInterval = null;

  // Fetch the latest recorded session so the "View last session" button can load it.
  onMount(async () => {
    try {
      const res = await fetch('/api/sessions/latest');
      if (res.ok) latestSession = await res.json();
    } catch { /* non-critical */ }
  });

  // When nextSession arrives, start a live countdown from startsInSeconds.
  $: if ($nextSession) {
    const receivedAt = Date.now();
    const targetMs   = receivedAt + $nextSession.startsInSeconds * 1000;

    if (countdownInterval) clearInterval(countdownInterval);
    countdown = Math.max(0, Math.round((targetMs - Date.now()) / 1000));

    countdownInterval = setInterval(() => {
      countdown = Math.max(0, Math.round((targetMs - Date.now()) / 1000));
      if (countdown === 0) clearInterval(countdownInterval);
    }, 1000);
  }

  onDestroy(() => { if (countdownInterval) clearInterval(countdownInterval); });

  function formatCountdown(secs) {
    if (secs == null || secs <= 0) return null;
    const h = Math.floor(secs / 3600);
    const m = Math.floor((secs % 3600) / 60);
    const s = secs % 60;
    if (h > 0) return `${h}h ${m}m`;
    if (m > 0) return `${m}m ${String(s).padStart(2, '0')}s`;
    return `${s}s`;
  }

  const SESSION_LABEL = {
    FP1: 'Practice 1', FP2: 'Practice 2', FP3: 'Practice 3',
    QUALIFYING: 'Qualifying', SPRINT: 'Sprint', RACE: 'Race',
    SPRINT_QUALIFYING: 'Sprint Qualifying'
  };

  function sessionLabel(type) {
    return SESSION_LABEL[type] ?? type ?? '';
  }
</script>

<div class="idle">
  <div class="logo">F1 Analytics</div>

  <p class="no-session">No active session right now.</p>

  <!-- Next session countdown -->
  {#if $nextSession}
    <div class="next-card">
      <div class="next-label">Next session</div>
      <div class="next-name">{$nextSession.sessionName}</div>
      {#if countdown != null && countdown > 0}
        <div class="countdown">Starting in {formatCountdown(countdown)}</div>
      {:else if countdown === 0}
        <div class="countdown starting">Starting now…</div>
      {/if}
    </div>
  {/if}

  <!-- View last session -->
  {#if latestSession}
    <div class="last-session">
      <div class="last-label">Last session</div>
      <div class="last-name">{latestSession.name} — {sessionLabel(latestSession.type)}</div>
      <button class="view-btn" on:click={() => connectToReplay(latestSession.key)}>
        View last session
      </button>
    </div>
  {/if}

  <!-- SSE connection status -->
  <p class="conn-status"
     class:connected={$connectionStatus === 'connected'}
     class:reconnecting={$connectionStatus === 'reconnecting'}>
    {#if $connectionStatus === 'connected'}
      <span class="dot"></span> Waiting for next session…
    {:else if $connectionStatus === 'reconnecting'}
      Reconnecting…
    {:else}
      Connecting…
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
    gap: 1.5rem;
    text-align: center;
    padding: 2rem;
  }

  .logo {
    font-size: 2rem;
    font-weight: bold;
    letter-spacing: 0.12em;
    color: var(--text-primary);
  }

  .no-session {
    font-size: 1rem;
    color: var(--text-secondary);
    margin: 0;
  }

  /* ── Next session card ── */
  .next-card {
    background-color: var(--bg-secondary);
    border: 1px solid #333;
    border-radius: 6px;
    padding: 1rem 2rem;
    min-width: 240px;
  }

  .next-label {
    font-size: 0.7rem;
    text-transform: uppercase;
    letter-spacing: 0.1em;
    color: var(--text-secondary);
    margin-bottom: 0.4rem;
  }

  .next-name {
    font-size: 1.1rem;
    font-weight: bold;
    color: var(--text-primary);
  }

  .countdown {
    margin-top: 0.4rem;
    font-size: 0.85rem;
    font-family: monospace;
    color: #FF8000;
  }

  .countdown.starting {
    color: #00C853;
    font-weight: bold;
  }

  /* ── Last session card ── */
  .last-session {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 0.5rem;
  }

  .last-label {
    font-size: 0.7rem;
    text-transform: uppercase;
    letter-spacing: 0.1em;
    color: var(--text-secondary);
  }

  .last-name {
    font-size: 0.9rem;
    color: var(--text-secondary);
  }

  .view-btn {
    margin-top: 0.25rem;
    padding: 0.5rem 1.25rem;
    background-color: #E8002D;
    color: #fff;
    border: none;
    border-radius: 4px;
    font-size: 0.85rem;
    font-weight: bold;
    letter-spacing: 0.05em;
    cursor: pointer;
    transition: background-color 0.2s;
  }

  .view-btn:hover { background-color: #c0001e; }

  /* ── Connection status ── */
  .conn-status {
    font-size: 0.8rem;
    color: var(--text-secondary);
    display: flex;
    align-items: center;
    gap: 0.4rem;
    margin: 0;
  }

  .conn-status.connected     { color: #00C853; }
  .conn-status.reconnecting  { color: #FF8000; }

  .dot {
    display: inline-block;
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: #00C853;
    animation: blink 1s step-start infinite;
  }

  @keyframes blink {
    0%, 100% { opacity: 1; }
    50%       { opacity: 0; }
  }
</style>
