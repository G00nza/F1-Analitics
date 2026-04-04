<script>
  import { slide } from 'svelte/transition';
  import { sessionState } from '../stores/session.js';

  let expanded = true;

  function toggleExpanded() { expanded = !expanded; }

  function rcIcon(msg) {
    const flag = msg.flag?.toUpperCase() ?? '';
    const text = msg.message?.toUpperCase() ?? '';
    if (flag === 'RED'    || text.includes('RED FLAG'))              return '🔴';
    if (flag === 'YELLOW' || flag === 'DOUBLE YELLOW'
                          || text.includes('YELLOW'))                return '🟡';
    if (flag === 'GREEN'  || flag === 'CLEAR'
                          || text.includes('TRACK CLEAR'))           return '🟢';
    if (text.includes('SAFETY CAR') && !text.includes('VIRTUAL'))   return '🚗';
    if (text.includes('VIRTUAL SAFETY CAR') || text.includes('VSC')) return '🟠';
    if (flag === 'CHEQUERED' || text.includes('CHEQUERED'))          return '🏁';
    return '📻';
  }

  function rcColor(msg) {
    const flag = msg.flag?.toUpperCase() ?? '';
    const text = msg.message?.toUpperCase() ?? '';
    if (flag === 'RED'    || text.includes('RED FLAG'))              return '#FF5252';
    if (flag === 'YELLOW' || flag === 'DOUBLE YELLOW'
                          || text.includes('YELLOW'))                return '#FFF200';
    if (flag === 'GREEN'  || flag === 'CLEAR'
                          || text.includes('TRACK CLEAR'))           return '#00C853';
    if (text.includes('SAFETY CAR') || text.includes('VSC'))        return '#FF8000';
    return 'var(--text-secondary)';
  }

  function formatTime(ts) {
    if (!ts) return '';
    try {
      return new Date(ts).toLocaleTimeString('en-GB', {
        hour: '2-digit', minute: '2-digit', second: '2-digit'
      });
    } catch { return ''; }
  }

  $: messages = $sessionState?.raceControlMessages ?? [];
  $: visible  = messages.slice(-5);
</script>

{#if messages.length}
  <div class="race-control">
    <!-- Header / toggle -->
    <button class="rc-header" on:click={toggleExpanded} aria-expanded={expanded}>
      <span class="rc-title">Race Control</span>
      <span class="rc-count">{messages.length} msg{messages.length !== 1 ? 's' : ''}</span>
      <span class="rc-chevron" class:open={expanded}>▲</span>
    </button>

    <!-- Message list -->
    {#if expanded}
      <div class="rc-messages" transition:slide={{ duration: 250 }}>
        {#each visible as msg (msg.timestamp)}
          <div class="message" style="border-left-color: {rcColor(msg)}"
               in:slide={{ duration: 200 }}>
            <span class="icon">{rcIcon(msg)}</span>
            <span class="time">{formatTime(msg.timestamp)}</span>
            <span class="text">{msg.message}</span>
          </div>
        {/each}
      </div>
    {/if}
  </div>
{/if}

<style>
  .race-control {
    position: sticky;
    bottom: 0;
    background-color: var(--bg-secondary);
    border-top: 1px solid #333;
    z-index: 10;
  }

  .rc-header {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    width: 100%;
    padding: 0.35rem 1rem;
    background: none;
    border: none;
    border-bottom: 1px solid #2a2a3a;
    color: var(--text-secondary);
    font-size: 0.75rem;
    cursor: pointer;
    text-align: left;
  }

  .rc-header:hover { background-color: #252535; }

  .rc-title {
    font-weight: bold;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: var(--text-primary);
    flex: 1;
  }

  .rc-count { font-size: 0.7rem; }

  .rc-chevron {
    font-size: 0.6rem;
    transition: transform 0.2s;
    transform: rotate(180deg);
  }
  .rc-chevron.open { transform: rotate(0deg); }

  .rc-messages {
    max-height: 130px;
    overflow-y: auto;
    padding: 0.25rem 0;
  }

  .message {
    display: flex;
    align-items: baseline;
    gap: 0.5rem;
    padding: 0.25rem 1rem;
    border-left: 3px solid transparent;
    font-size: 0.8rem;
  }

  .icon { font-size: 0.75rem; flex-shrink: 0; }

  .time {
    color: var(--text-secondary);
    font-family: monospace;
    font-size: 0.75rem;
    white-space: nowrap;
    flex-shrink: 0;
  }

  .text { color: var(--text-primary); }

  /* Mobile: collapsed by default hint */
  @media (max-width: 599px) {
    .rc-messages { max-height: 110px; }
  }
</style>
