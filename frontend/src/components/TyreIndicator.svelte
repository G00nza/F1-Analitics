<script>
  import { tyreColor } from '../lib/f1utils.js';

  export let compound = null;
  export let laps = null;
</script>

{#if compound}
  <span class="tyre" style="color: {tyreColor(compound)}">
    {compound[0]}
    {#if laps != null}<sup>{laps}</sup>{/if}
  </span>
{/if}

<style>
  .tyre {
    font-weight: bold;
    font-size: 0.85rem;
    font-family: monospace;
  }
</style>
