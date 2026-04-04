<script>
  import { sessionState } from '../stores/session.js';

  const FLAG_COLORS = {
    YELLOW: '#FFF200',
    RED:    '#FF1E00',
    GREEN:  '#00C853',
    SC:     '#FF8000',
    VSC:    '#FF8000',
  };

  function msgColor(msg) {
    const text = msg.message?.toUpperCase() ?? '';
    if (text.includes('YELLOW')) return FLAG_COLORS.YELLOW;
    if (text.includes('RED FLAG')) return FLAG_COLORS.RED;
    if (text.includes('SAFETY CAR')) return FLAG_COLORS.SC;
    if (text.includes('VIRTUAL SAFETY CAR') || text.includes('VSC')) return FLAG_COLORS.VSC;
    if (text.includes('CLEAR') || text.includes('GREEN')) return FLAG_COLORS.GREEN;
    return 'var(--text-secondary)';
  }
</script>

{#if $sessionState?.raceControlMessages?.length}
  <div class="race-control">
    {#each $sessionState.raceControlMessages.slice(-5) as msg (msg.timestamp)}
      <div class="message" style="border-left-color: {msgColor(msg)}">
        <span class="time">{msg.timestamp ?? ''}</span>
        <span class="text">{msg.message}</span>
      </div>
    {/each}
  </div>
{/if}

<style>
  .race-control {
    background-color: var(--bg-secondary);
    border-top: 1px solid #333;
    padding: 0.5rem 1rem;
    max-height: 120px;
    overflow-y: auto;
  }

  .message {
    display: flex;
    gap: 0.75rem;
    padding: 0.25rem 0.5rem;
    border-left: 3px solid transparent;
    font-size: 0.8rem;
    animation: fadeIn 0.3s ease-in;
  }

  .time {
    color: var(--text-secondary);
    white-space: nowrap;
  }

  .text {
    color: var(--text-primary);
  }

  @keyframes fadeIn {
    from { opacity: 0; transform: translateY(-4px); }
    to   { opacity: 1; transform: translateY(0); }
  }
</style>
