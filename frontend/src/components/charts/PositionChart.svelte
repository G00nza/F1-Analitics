<script>
  import { onMount, onDestroy } from 'svelte';
  import Chart from 'chart.js/auto';

  /** @type {Array} position data from /api/sessions/{key}/positions */
  export let positions = [];

  let canvas;
  let chart = null;

  $: if (chart && positions.length > 0) refreshChart(positions);

  function buildDatasets(posData) {
    const byDriver = new Map();
    for (const p of posData) {
      if (!byDriver.has(p.driverNumber)) {
        byDriver.set(p.driverNumber, { code: p.driverCode, color: p.teamColor, entries: [] });
      }
      byDriver.get(p.driverNumber).entries.push(p);
    }

    return [...byDriver.values()].map(d => {
      const sorted = [...d.entries].sort((a, b) => a.lapNumber - b.lapNumber);
      return {
        label: d.code,
        data: sorted.map(e => ({ x: e.lapNumber, y: e.position })),
        borderColor: d.color,
        backgroundColor: 'transparent',
        borderWidth: 2,
        tension: 0.15,
        spanGaps: true,
        pointRadius: 2,
        pointHoverRadius: 5,
        pointBackgroundColor: d.color,
        pointBorderColor: d.color,
      };
    });
  }

  function refreshChart(posData) {
    if (!chart) return;
    chart.data.datasets = buildDatasets(posData);
    chart.update('none');
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
            title: { display: true, text: 'Lap', color: '#AAA' },
            grid: { color: '#252535' },
            ticks: { color: '#AAA', stepSize: 5 },
          },
          y: {
            reverse: true,
            min: 1,
            suggestedMax: 4,
            title: { display: true, text: 'Position', color: '#AAA' },
            grid: { color: '#252535' },
            ticks: {
              color: '#AAA',
              stepSize: 1,
              callback: (v) => Number.isInteger(v) ? `P${v}` : '',
            },
          },
        },
        plugins: {
          legend: {
            labels: { color: '#DDD', usePointStyle: true, pointStyle: 'line', boxWidth: 20 },
          },
          tooltip: {
            callbacks: {
              label: (ctx) => ` ${ctx.dataset.label}: P${ctx.raw.y}`,
            },
          },
        },
      },
    });

    if (positions.length) refreshChart(positions);
  });

  onDestroy(() => chart?.destroy());
</script>

<div class="chart-wrapper">
  <canvas bind:this={canvas}></canvas>
</div>

<style>
  .chart-wrapper {
    position: relative;
    height: 320px;
    width: 100%;
  }
</style>
