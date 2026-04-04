<script>
  import { sessionState, connectionStatus, nextSession } from '../stores/session.js';
  import { formatTimeRemaining, windDirectionLabel } from '../lib/f1utils.js';

  $: session  = $sessionState;
  $: weather  = session?.weather ?? null;
  $: status   = session?.sessionStatus?.toLowerCase() ?? '';
  $: isActive = status === 'active';
  $: isEnded  = status === 'finished' || status === 'ended' || status === 'aborted';
  $: timeStr  = formatTimeRemaining(session?.timeRemainingMs);
  $: hotTrack = (weather?.trackTemp ?? 0) > 50;
</script>

<header>
  <!-- Row 1: brand / session / live indicator -->
  <div class="row row-main">
    <span class="brand">F1 Analytics</span>

    <div class="session-badge">
      {#if session}
        <span class="session-name">{session.sessionName ?? ''}</span>
        {#if isActive}
          <span class="status-active"><span class="pulse"></span>ACTIVE</span>
        {:else if isEnded}
          <span class="status-ended">ENDED</span>
        {:else if status}
          <span class="status-other">{session.sessionStatus}</span>
        {/if}
        {#if timeStr}
          <span class="time-remaining">{timeStr}</span>
        {/if}
      {:else if $nextSession}
        <span class="next-session">Next: {$nextSession.sessionName}</span>
      {/if}
    </div>

    <div class="connection"
         class:live={$connectionStatus === 'connected'}
         class:reconnecting={$connectionStatus === 'reconnecting'}>
      {#if $connectionStatus === 'connected'}
        <span class="dot"></span>LIVE
      {:else if $connectionStatus === 'reconnecting'}
        RECONNECTING
      {:else}
        CONNECTING
      {/if}
    </div>
  </div>

  <!-- Row 2: event / circuit (only when session active) -->
  {#if session && (session.officialName || session.circuitName)}
    <div class="row row-event">
      {#if session.officialName}
        <span class="official-name">{session.officialName}</span>
      {/if}
      {#if session.circuitName}
        <span class="circuit-sep">{session.officialName ? '—' : ''}</span>
        <span class="circuit-name">{session.circuitName}</span>
      {/if}
    </div>
  {/if}

  <!-- Row 3: weather -->
  {#if weather}
    <div class="row row-weather">
      <span class="weather-item" class:track-hot={hotTrack}>
        🌡 Air {weather.airTemp != null ? `${weather.airTemp.toFixed(0)}°` : '--'}
        &nbsp;Track {weather.trackTemp != null ? `${weather.trackTemp.toFixed(0)}°` : '--'}
        {#if hotTrack}<span class="hot-warning" title="Track temp > 50°C — high tyre degradation">⚠</span>{/if}
      </span>
      {#if weather.humidity != null}
        <span class="weather-item">💧 {weather.humidity.toFixed(0)}%</span>
      {/if}
      {#if weather.windSpeed != null}
        <span class="weather-item">
          💨 {weather.windSpeed.toFixed(0)} km/h
          {windDirectionLabel(weather.windDirection)}
        </span>
      {/if}
      {#if weather.rainfall}
        <span class="weather-item rain">🌧 Rain</span>
      {/if}
    </div>
  {/if}
</header>

<style>
  header {
    display: flex;
    flex-direction: column;
    background-color: var(--bg-secondary);
    border-bottom: 2px solid #E8002D;
    padding: 0.5rem 1rem;
    gap: 0.25rem;
  }

  .row {
    display: flex;
    align-items: center;
    gap: 0.75rem;
  }

  /* ── Row 1 ── */
  .row-main {
    justify-content: space-between;
  }

  .brand {
    font-size: 1.1rem;
    font-weight: bold;
    letter-spacing: 0.05em;
    white-space: nowrap;
  }

  .session-badge {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    font-size: 0.8rem;
    font-weight: bold;
    flex: 1;
    justify-content: center;
  }

  .session-name {
    text-transform: uppercase;
    letter-spacing: 0.08em;
    color: var(--text-primary);
  }

  .status-active {
    display: flex;
    align-items: center;
    gap: 0.3rem;
    color: #00C853;
    font-size: 0.75rem;
  }

  .pulse {
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

  .status-ended {
    color: var(--text-secondary);
    font-size: 0.75rem;
  }

  .status-other {
    color: var(--text-secondary);
    font-size: 0.75rem;
    text-transform: uppercase;
  }

  .time-remaining {
    font-family: monospace;
    color: var(--text-primary);
    font-size: 0.85rem;
  }

  .next-session {
    color: var(--text-secondary);
    font-size: 0.8rem;
  }

  /* ── Connection indicator ── */
  .connection {
    font-size: 0.7rem;
    font-weight: bold;
    display: flex;
    align-items: center;
    gap: 0.35rem;
    color: var(--text-secondary);
    white-space: nowrap;
  }

  .connection.live         { color: #00C853; }
  .connection.reconnecting { color: #FF8000; }

  .dot {
    display: inline-block;
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: #00C853;
    animation: blink 1s step-start infinite;
  }

  /* ── Row 2: event / circuit ── */
  .row-event {
    font-size: 0.8rem;
    color: var(--text-secondary);
    flex-wrap: wrap;
  }

  .official-name {
    color: var(--text-primary);
    font-weight: 500;
  }

  .circuit-sep {
    color: var(--text-secondary);
  }

  .circuit-name {
    color: var(--text-secondary);
  }

  /* ── Row 3: weather ── */
  .row-weather {
    font-size: 0.75rem;
    color: var(--text-secondary);
    flex-wrap: wrap;
    gap: 0.5rem;
  }

  .weather-item {
    display: flex;
    align-items: center;
    gap: 0.2rem;
    white-space: nowrap;
  }

  .weather-item.track-hot {
    color: #FF8000;
  }

  .hot-warning {
    color: #FF8000;
    font-size: 0.7rem;
    margin-left: 0.15rem;
  }

  .rain {
    color: #64C4FF;
  }

  /* ── Mobile: compress rows ── */
  @media (max-width: 599px) {
    .session-badge {
      font-size: 0.75rem;
      justify-content: flex-start;
    }

    .row-weather {
      font-size: 0.7rem;
    }
  }
</style>
