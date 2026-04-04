<script>
  import { onMount, onDestroy } from 'svelte';
  import Chart from 'chart.js/auto';

  /** @type {Array} lap data from /api/sessions/{key}/laps (must include gapToLeaderMs) */
  export let laps = [];

  let canvas;
  let chart = null;

  $: if (chart && laps.length > 0) refreshChart(laps);

  function buildDatasets(lapData) {
    const byDriver = new Map();
    for (const lap of lapData) {
      if (!byDriver.has(lap.driverNumber)) {
        byDriver.set(lap.driverNumber, { code: lap.driverCode, color: lap.teamColor, entries: [] });
      }
      // Include leader (null gap = 0) and any driver with a valid gap
      const gap = lap.gapToLeaderMs;
      if (gap !== null || lap.gapToLeaderMs === null) {
        byDriver.get(lap.driverNumber).entries.push(lap);
      }
    }

    return [...byDriver.values()].map(d => {
      const sorted = [...d.entries].sort((a, b) => a.lapNumber - b.lapNumber);
      const points = [];
      const radii = [];

      for (const lap of sorted) {
        // null gapToLeaderMs means this driver is the leader (0s)
        const gapSec = lap.gapToLeaderMs != null ? lap.gapToLeaderMs / 1000 : 0;
        points.push({
          x: lap.lapNumber,
          y: gapSec,
          pitOutLap: lap.pitOutLap,
          pitInLap: lap.pitInLap,
        });
        radii.push(lap.pitOutLap || lap.pitInLap ? 4 : 2);
      }

      return {
        label: d.code,
        data: points,
        borderColor: d.color,
        backgroundColor: 'transparent',
        borderWidth: 1.5,
        tension: 0.1,
        spanGaps: true,
        pointRadius: radii,
        pointHoverRadius: 5,
        pointBackgroundColor: d.color,
        pointBorderColor: d.color,
      };
    });
  }

  function refreshChart(lapData) {
    if (!chart) return;
    chart.data.datasets = buildDatasets(lapData);
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

    if (laps.length) refreshChart(laps);
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
