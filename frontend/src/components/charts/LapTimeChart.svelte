<script>
  import { onMount, onDestroy } from 'svelte';
  import Chart from 'chart.js/auto';
  import { formatLapTime } from '../../lib/f1utils.js';

  /** @type {Array} lap data from /api/sessions/{key}/laps */
  export let laps = [];

  let canvas;
  let chart = null;

  $: if (chart && laps.length > 0) refreshChart(laps);

  function buildDatasets(lapData) {
    const byDriver = new Map();
    for (const lap of lapData) {
      if (!byDriver.has(lap.driverNumber)) {
        byDriver.set(lap.driverNumber, { code: lap.driverCode, color: lap.teamColor, laps: [] });
      }
      byDriver.get(lap.driverNumber).laps.push(lap);
    }

    return [...byDriver.values()].map(d => {
      const sorted = [...d.laps].sort((a, b) => a.lapNumber - b.lapNumber);
      const points = [];
      const radii = [];
      const bgColors = [];
      const borderColors = [];

      for (const lap of sorted) {
        // Insert a null gap before each out-lap to break the line at pit stops
        if (lap.pitOutLap) {
          points.push({ x: lap.lapNumber - 0.5, y: null });
          radii.push(0);
          bgColors.push('transparent');
          borderColors.push('transparent');
        }
        points.push({
          x: lap.lapNumber,
          y: lap.lapTimeMs != null ? lap.lapTimeMs / 1000 : null,
          pitOutLap: lap.pitOutLap,
          isPersonalBest: lap.isPersonalBest,
          compound: lap.compound,
        });
        radii.push(lap.isPersonalBest ? 6 : lap.pitOutLap ? 4 : 2);
        bgColors.push(
          lap.isPersonalBest ? '#BF5FFF' :
          lap.pitOutLap       ? 'transparent' :
          d.color
        );
        borderColors.push(lap.pitOutLap ? '#666' : d.color);
      }

      return {
        label: d.code,
        data: points,
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
    height: 340px;
    width: 100%;
  }
</style>
