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
      borderWidth: 2,
      tension: 0.15,
      spanGaps: true,
      pointRadius: 2,
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
