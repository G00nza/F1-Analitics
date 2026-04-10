<script>
  import { onMount, onDestroy } from 'svelte';
  import Chart from 'chart.js/auto';

  /** @type {Array} pre-computed datasets from /api/sessions/{key}/charts */
  export let datasets = [];

  let canvas;
  let chart = null;

  $: if (chart) {
    chart.data.datasets = toChartDatasets(datasets);
    chart.update('none');
  }

  function toChartDatasets(data) {
    return data.map(d => ({
      label: d.label,
      data: d.points,
      borderColor: d.color,
      backgroundColor: 'transparent',
      borderWidth: 1.5,
      tension: 0.1,
      spanGaps: true,
      pointRadius: d.points.map(p => (p.pitOutLap || p.pitInLap) ? 4 : 2),
      pointHoverRadius: 5,
      pointBackgroundColor: d.color,
      pointBorderColor: d.color,
    }));
  }

  onMount(() => {
    chart = new Chart(canvas, {
      type: 'line',
      data: { datasets: toChartDatasets(datasets) },
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
            title: { display: true, text: 'Gap to Leader (s)', color: '#AAA' },
            grid: { color: '#252535' },
            ticks: {
              color: '#AAA',
              callback: (v) => v === 0 ? 'Leader' : `+${v.toFixed(0)}s`,
            },
          },
        },
        plugins: {
          legend: {
            labels: { color: '#DDD', usePointStyle: true, pointStyle: 'line', boxWidth: 20 },
          },
          tooltip: {
            callbacks: {
              label: (ctx) => {
                const p = ctx.raw;
                const gap = p.y === 0 ? 'Leader' : `+${p.y.toFixed(3)}s`;
                const note = p.pitInLap ? ' (PIT)' : p.pitOutLap ? ' (OUT)' : '';
                return ` ${ctx.dataset.label}: ${gap}${note}`;
              },
            },
          },
        },
      },
    });
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
