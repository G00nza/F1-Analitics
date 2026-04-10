<script>
  import { onMount, onDestroy } from 'svelte';
  import Chart from 'chart.js/auto';

  /** @type {Array} pre-computed datasets from /api/sessions/{key}/charts */
  export let datasets = [];

  let canvas;
  let chart = null;
  let selectedCompound = 'ALL';

  const COMPOUNDS = ['ALL', 'SOFT', 'MEDIUM', 'HARD', 'INTER', 'WET'];

  $: filtered = selectedCompound === 'ALL'
    ? datasets
    : datasets.filter(d => d.compound === selectedCompound);

  $: if (chart) {
    chart.data.datasets = toChartDatasets(filtered);
    chart.update('none');
  }

  function toChartDatasets(data) {
    return data.map(d => ({
      label: d.label,
      data: d.points,
      borderColor: d.color,
      backgroundColor: d.color + '18',
      borderWidth: 1.5,
      tension: 0.3,
      pointRadius: 2,
      pointHoverRadius: 5,
      pointBackgroundColor: d.color,
    }));
  }

  onMount(() => {
    chart = new Chart(canvas, {
      type: 'line',
      data: { datasets: toChartDatasets(filtered) },
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
