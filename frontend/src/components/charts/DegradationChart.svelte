<script>
  import { onMount, onDestroy } from 'svelte';
  import Chart from 'chart.js/auto';

  /** @type {Array} lap data from /api/sessions/{key}/laps */
  export let laps = [];
  /** @type {Array} stint data from /api/sessions/{key}/stints */
  export let stints = [];

  let canvas;
  let chart = null;
  let selectedCompound = 'ALL';

  const COMPOUNDS = ['ALL', 'SOFT', 'MEDIUM', 'HARD', 'INTER', 'WET'];

  $: datasets = buildDatasets(laps, stints, selectedCompound);
  $: if (chart) {
    chart.data.datasets = datasets;
    chart.update('none');
  }

  function buildDatasets(lapData, stintData, compound) {
    if (!lapData.length || !stintData.length) return [];

    // Index laps by driverNumber-stintNumber
    const lapMap = new Map();
    for (const lap of lapData) {
      const key = `${lap.driverNumber}-${lap.stintNumber}`;
      if (!lapMap.has(key)) lapMap.set(key, []);
      lapMap.get(key).push(lap);
    }

    const result = [];
    for (const stint of stintData) {
      if (compound !== 'ALL' && stint.compound !== compound) continue;

      const key = `${stint.driverNumber}-${stint.stintNumber}`;
      const validLaps = (lapMap.get(key) || [])
        .filter(l => !l.pitOutLap && !l.pitInLap && l.lapTimeMs != null)
        .sort((a, b) => a.lapNumber - b.lapNumber);

      // Only show stints with more than 5 valid laps
      if (validLaps.length <= 5) continue;

      const baseLapMs = validLaps[0].lapTimeMs;
      const color = validLaps[0].teamColor;

      result.push({
        label: `${stint.driverCode} S${stint.stintNumber} (${stint.compound})`,
        data: validLaps.map((l, i) => ({
          x: i + 1,
          y: (l.lapTimeMs - baseLapMs) / 1000,
          lapNumber: l.lapNumber,
          compound: l.compound,
        })),
        borderColor: color,
        backgroundColor: color + '18',
        borderWidth: 1.5,
        tension: 0.3,
        pointRadius: 2,
        pointHoverRadius: 5,
        pointBackgroundColor: color,
      });
    }
    return result;
  }

  onMount(() => {
    chart = new Chart(canvas, {
      type: 'line',
      data: { datasets: [] },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: 'index', intersect: false },
        scales: {
          x: {
            type: 'linear',
            title: { display: true, text: 'Lap in stint', color: '#AAA' },
            grid: { color: '#252535' },
            ticks: { color: '#AAA', stepSize: 2 },
          },
          y: {
            title: { display: true, text: 'Delta to stint baseline (s)', color: '#AAA' },
            grid: { color: '#252535' },
            ticks: {
              color: '#AAA',
              callback: (v) => `${v >= 0 ? '+' : ''}${v.toFixed(2)}s`,
            },
          },
        },
        plugins: {
          legend: { labels: { color: '#DDD', font: { size: 11 } } },
          tooltip: {
            callbacks: {
              label: (ctx) => {
                const p = ctx.raw;
                const delta = p.y >= 0 ? `+${p.y.toFixed(3)}s` : `${p.y.toFixed(3)}s`;
                return ` ${ctx.dataset.label}: ${delta}  (Lap ${p.lapNumber})`;
              },
            },
          },
        },
      },
    });

    if (datasets.length) {
      chart.data.datasets = datasets;
      chart.update('none');
    }
  });

  onDestroy(() => chart?.destroy());
</script>

<div class="controls">
  {#each COMPOUNDS as c}
    <button class:active={selectedCompound === c} on:click={() => (selectedCompound = c)}>{c}</button>
  {/each}
</div>

<div class="chart-wrapper">
  <canvas bind:this={canvas}></canvas>
</div>

<style>
  .controls {
    display: flex;
    gap: 0.4rem;
    padding: 0.5rem 0 0.75rem;
    flex-wrap: wrap;
  }

  button {
    padding: 0.25rem 0.65rem;
    font-size: 0.72rem;
    font-weight: bold;
    letter-spacing: 0.06em;
    background: var(--bg-secondary);
    color: var(--text-secondary);
    border: 1px solid #333;
    border-radius: 3px;
    cursor: pointer;
    transition: all 0.15s;
  }

  button.active,
  button:hover {
    background: #E8002D;
    color: #fff;
    border-color: #E8002D;
  }

  .chart-wrapper {
    position: relative;
    height: 320px;
    width: 100%;
  }
</style>
