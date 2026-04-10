<script>
  import { onMount, onDestroy } from 'svelte';
  import Chart from 'chart.js/auto';
  import { formatLapTime } from '../../lib/f1utils.js';

  /** @type {Array} pre-computed datasets from /api/sessions/{key}/charts */
  export let datasets = [];

  let canvas;
  let chart = null;

  $: if (chart) {
    chart.data.datasets = toChartDatasets(datasets);
    chart.update('none');
  }

  function toChartDatasets(data) {
    return data.map(d => {
      const radii = [];
      const bgColors = [];
      const borderColors = [];

      for (const p of d.points) {
        radii.push(p.isPersonalBest ? 6 : p.pitOutLap ? 4 : (p.y == null ? 0 : 2));
        bgColors.push(p.isPersonalBest ? '#BF5FFF' : p.pitOutLap ? 'transparent' : d.color);
        borderColors.push(p.pitOutLap ? '#666' : d.color);
      }

      return {
        label: d.label,
        data: d.points,
        borderColor: d.color,
        backgroundColor: d.color + '18',
        borderWidth: 1.5,
        tension: 0,
        spanGaps: false,
        pointRadius: radii,
        pointBackgroundColor: bgColors,
        pointBorderColor: borderColors,
        pointBorderWidth: 1.5,
        pointHoverRadius: 5,
      };
    });
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
            title: { display: true, text: 'Lap Time', color: '#AAA' },
            grid: { color: '#252535' },
            ticks: {
              color: '#AAA',
              callback: (v) => {
                const m = Math.floor(v / 60);
                const s = v % 60;
                const [si, ms] = s.toFixed(3).split('.');
                return `${m}:${String(si).padStart(2, '0')}.${ms}`;
              },
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
                if (p?.y == null) return null;
                const time = formatLapTime(Math.round(p.y * 1000));
                const flag = p.isPersonalBest ? ' ★' : p.pitOutLap ? ' (OUT)' : '';
                const cmp  = p.compound ? ` · ${p.compound}` : '';
                return ` ${ctx.dataset.label}: ${time}${flag}${cmp}`;
              },
              filter: (item) => item.raw?.y != null,
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
    height: 340px;
    width: 100%;
  }
</style>
