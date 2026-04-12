<script>
  export let sessions = [];
  export let raceKey = null;

  const COMPOUND_COLORS = { SOFT: '#E8002D', MEDIUM: '#FFF200', HARD: '#FFFFFF', INTER: '#39B54A', WET: '#0067FF' };
  const COMPOUND_TEXT   = { SOFT: '#111', MEDIUM: '#111', HARD: '#111', INTER: '#111', WET: '#fff' };

  let subTab = 'preview';

  // ── Pre-race data ──────────────────────────────────────────────────────────
  let totalLaps     = 57;
  let preRaceData   = null;
  let preRaceLoading = false;
  let preRaceError   = null;

  // ── Post-race data ─────────────────────────────────────────────────────────
  let postRaceData   = null;
  let postRaceLoading = false;
  let postRaceError   = null;

  // ── SC review data ─────────────────────────────────────────────────────────
  let scData   = null;
  let scLoading = false;
  let scError   = null;

  $: raceSession = sessions.find(s => s.type === 'RACE');

  $: if (subTab === 'preview' && raceKey && !preRaceData && !preRaceLoading) loadPreRace(raceKey);

  $: if (subTab === 'postrace' && raceSession && !postRaceData && !postRaceLoading) loadPostRace(raceSession.key);
  $: if (subTab === 'safetycar' && raceSession && !scData && !scLoading) loadSC(raceSession.key);

  async function loadPreRace(key) {
    preRaceLoading = true; preRaceError = null; preRaceData = null;
    try {
      const res = await fetch(`/api/races/${key}/strategy/preview?totalLaps=${totalLaps}`);
      if (!res.ok) throw new Error(res.statusText);
      preRaceData = await res.json();
    } catch (e) { preRaceError = 'Failed to load pre-race strategy'; }
    finally { preRaceLoading = false; }
  }

  function reloadPreRace() {
    if (raceKey) loadPreRace(raceKey);
  }

  async function loadPostRace(key) {
    postRaceLoading = true; postRaceError = null;
    try {
      const res = await fetch(`/api/sessions/${key}/strategy/post-race`);
      if (!res.ok) throw new Error(res.statusText);
      postRaceData = await res.json();
    } catch (e) { postRaceError = 'Failed to load post-race strategy'; }
    finally { postRaceLoading = false; }
  }

  async function loadSC(key) {
    scLoading = true; scError = null;
    try {
      const res = await fetch(`/api/sessions/${key}/strategy/safety-car/review`);
      if (!res.ok) throw new Error(res.statusText);
      scData = await res.json();
    } catch (e) { scError = 'Failed to load safety car data'; }
    finally { scLoading = false; }
  }

  function compoundBg(c)   { return COMPOUND_COLORS[c] ?? '#555'; }
  function compoundFg(c)   { return COMPOUND_TEXT[c]   ?? '#fff'; }

  function stintLabel(stint) {
    const laps = stint.laps != null ? `${stint.laps}L` : (stint.lapStart && stint.lapEnd ? `${stint.lapEnd - stint.lapStart + 1}L` : '?');
    return `${stint.compound ?? '?'} (${laps})`;
  }

  function fmtWindow(w) {
    if (!w) return '—';
    return `L${w.lapFrom}–${w.lapTo}`;
  }
</script>

<!-- Sub-tabs -->
<div class="sub-tabs">
  <button class:active={subTab === 'preview'}  on:click={() => subTab = 'preview'}>Pre-Race Preview</button>
  <button class:active={subTab === 'postrace'} on:click={() => subTab = 'postrace'}>Post-Race Review</button>
  <button class:active={subTab === 'safetycar'} on:click={() => subTab = 'safetycar'}>Safety Car</button>
</div>

<!-- ── PRE-RACE PREVIEW ───────────────────────────────────────────────────── -->
{#if subTab === 'preview'}
  {#if !raceKey}
    <div class="state-msg">No race selected.</div>
  {:else if preRaceLoading}
    <div class="state-msg">Loading…</div>
  {:else if preRaceError}
    <div class="state-msg error">{preRaceError}</div>
  {:else if preRaceData}
    {#if !preRaceData.hasData}
      <div class="warning">Limited practice data available — strategy predictions may be inaccurate.</div>
    {/if}
    <div class="laps-control">
      <label>Race laps:
        <input type="number" min="20" max="80" bind:value={totalLaps} />
      </label>
      <button on:click={reloadPreRace}>Recalculate</button>
    </div>

    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Driver</th>
            <th>Expected Strategy</th>
            <th>Alt Strategy</th>
          </tr>
        </thead>
        <tbody>
          {#each preRaceData.drivers as d}
            <tr>
              <td class="driver-cell">
                <span class="dot" style="background:{COMPOUND_COLORS.SOFT}; background:{d.team ? 'var(--dot-color)' : '#888'}"></span>
                <strong>{d.driverCode}</strong>
              </td>
              <td>
                <div class="stint-row">
                  {#each d.expectedStrategy as stint, i}
                    {#if i > 0}<span class="arrow">→</span>{/if}
                    <div class="stint-chip">
                      <span class="compound-badge" style="background:{compoundBg(stint.compound)};color:{compoundFg(stint.compound)}">{stint.compound}</span>
                      <span class="stint-laps">{stint.laps}L</span>
                      {#if stint.pitWindow}<span class="pit-window">pit L{stint.pitWindow.lapFrom}–{stint.pitWindow.lapTo}</span>{/if}
                    </div>
                  {/each}
                </div>
              </td>
              <td>
                {#if d.altStrategy}
                  <div class="stint-row alt">
                    {#each d.altStrategy as stint, i}
                      {#if i > 0}<span class="arrow">→</span>{/if}
                      <span class="compound-badge" style="background:{compoundBg(stint.compound)};color:{compoundFg(stint.compound)}">{stint.compound} {stint.laps}L</span>
                    {/each}
                  </div>
                {:else}
                  <span class="dim">—</span>
                {/if}
              </td>
            </tr>
          {/each}
        </tbody>
      </table>
    </div>
  {/if}

<!-- ── POST-RACE REVIEW ───────────────────────────────────────────────────── -->
{:else if subTab === 'postrace'}
  {#if !raceSession}
    <div class="state-msg">No race session available.</div>
  {:else if postRaceLoading}
    <div class="state-msg">Loading…</div>
  {:else if postRaceError}
    <div class="state-msg error">{postRaceError}</div>
  {:else if postRaceData}
    <!-- Driver strategies -->
    <h4 class="section-title">Driver Strategies</h4>
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Pos</th>
            <th>Driver</th>
            <th>Stops</th>
            <th>Strategy</th>
          </tr>
        </thead>
        <tbody>
          {#each postRaceData.drivers.sort((a,b) => (a.finalPosition ?? 99) - (b.finalPosition ?? 99)) as d}
            <tr>
              <td class="pos">P{d.finalPosition ?? '—'}</td>
              <td class="driver-cell"><strong>{d.driverCode ?? d.driverNumber}</strong></td>
              <td class="center">{d.stops}</td>
              <td>
                <div class="stint-row">
                  {#each d.stints as s, i}
                    {#if i > 0}<span class="arrow">→</span>{/if}
                    <span class="compound-badge" style="background:{compoundBg(s.compound)};color:{compoundFg(s.compound)}">
                      {s.compound ?? '?'}{s.laps ? ` (${s.laps}L)` : ''}
                    </span>
                  {/each}
                </div>
              </td>
            </tr>
          {/each}
        </tbody>
      </table>
    </div>

    <!-- Strategy comparison -->
    <h4 class="section-title" style="margin-top:1.5rem">Stop-Count Comparison</h4>
    <div class="comparison-grid">
      {#each [['1-Stop', postRaceData.strategyComparison.oneStop], ['2-Stop', postRaceData.strategyComparison.twoStop], ['3+-Stop', postRaceData.strategyComparison.threeOrMore]] as [label, group]}
        {#if group}
          <div class="comparison-card">
            <div class="comp-label">{label}</div>
            <div class="comp-count">{group.driverCount} drivers</div>
            <div class="comp-avg">Avg P{group.avgFinishPosition?.toFixed(1) ?? '—'}</div>
            <div class="comp-drivers">{group.drivers.join(', ')}</div>
          </div>
        {/if}
      {/each}
    </div>

    <!-- Undercut results -->
    {#if postRaceData.undercutResults.length > 0}
      <h4 class="section-title" style="margin-top:1.5rem">Undercut Attempts</h4>
      <div class="table-wrap">
        <table>
          <thead>
            <tr><th>Lap</th><th>Attacker</th><th>Target</th><th>Outcome</th><th>Positions</th></tr>
          </thead>
          <tbody>
            {#each postRaceData.undercutResults as u}
              {@const success = (u.instigatorFinalPosition ?? 99) < (u.rivalFinalPosition ?? 99)}
              <tr>
                <td class="mono dim">L{u.lap ?? '?'}</td>
                <td><strong>{u.instigatorCode ?? '?'}</strong></td>
                <td>{u.rivalCode ?? '?'}</td>
                <td><span class="badge" class:badge-success={success} class:badge-fail={!success}>{success ? 'SUCCESS' : 'FAILED'}</span></td>
                <td class="mono dim">P{u.instigatorFinalPosition} vs P{u.rivalFinalPosition}</td>
              </tr>
            {/each}
          </tbody>
        </table>
      </div>
    {/if}

    <!-- SC beneficiaries -->
    {#if postRaceData.scBeneficiaries.length > 0}
      <h4 class="section-title" style="margin-top:1.5rem">Safety Car Beneficiaries</h4>
      <div class="table-wrap">
        <table>
          <thead>
            <tr><th>Driver</th><th>SC Lap</th><th>Pos at SC</th><th>Final Pos</th><th>Gained</th></tr>
          </thead>
          <tbody>
            {#each postRaceData.scBeneficiaries as b}
              <tr>
                <td><strong>{b.driverCode ?? '?'}</strong></td>
                <td class="mono dim">L{b.scLap ?? '?'}</td>
                <td class="mono">P{b.positionAtSc ?? '—'}</td>
                <td class="mono">P{b.finalPosition ?? '—'}</td>
                <td class="gained">+{b.positionsGained ?? 0}</td>
              </tr>
            {/each}
          </tbody>
        </table>
      </div>
    {/if}
  {/if}

<!-- ── SAFETY CAR REVIEW ──────────────────────────────────────────────────── -->
{:else if subTab === 'safetycar'}
  {#if !raceSession}
    <div class="state-msg">No race session available.</div>
  {:else if scLoading}
    <div class="state-msg">Loading…</div>
  {:else if scError}
    <div class="state-msg error">{scError}</div>
  {:else if scData}
    {#if scData.events.length === 0}
      <div class="state-msg">No safety car periods in this race.</div>
    {:else}
      {#each scData.events as event, ei}
        <h4 class="section-title">
          SC #{ei + 1} — Lap {event.scLap ?? '?'}{event.scEndLap ? `–${event.scEndLap}` : ''}
        </h4>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Driver</th>
                <th>Pos</th>
                <th>Tyre</th>
                <th>Age</th>
                <th>New Tyres</th>
                <th>Score</th>
                <th>Recommendation</th>
                <th>Pitted?</th>
                <th>Final Pos</th>
                <th>Correct?</th>
              </tr>
            </thead>
            <tbody>
              {#each event.drivers as d}
                <tr>
                  <td><strong>{d.driverCode ?? d.driverNumber}</strong></td>
                  <td class="mono">P{d.positionAtSc ?? '—'}</td>
                  <td>
                    {#if d.compound}
                      <span class="compound-badge sm" style="background:{compoundBg(d.compound)};color:{compoundFg(d.compound)}">{d.compound}</span>
                    {:else}—{/if}
                  </td>
                  <td class="mono dim">{d.tyreAgeLaps ?? '—'}L</td>
                  <td class="center">{d.hasNewTyresAvailable ? '✓' : '✗'}</td>
                  <td>
                    <div class="score-bar">
                      <div class="score-fill" style="width:{d.score}%;background:{d.score >= 76 ? '#E8002D' : d.score >= 51 ? '#FF8000' : d.score >= 26 ? '#FFF200' : '#555'}"></div>
                      <span class="score-val">{d.score}</span>
                    </div>
                  </td>
                  <td class="rec-msg">{d.message}</td>
                  <td class="center">{d.pittedDuringSc ? '✓' : '—'}</td>
                  <td class="mono">P{d.finalPosition ?? '—'}</td>
                  <td class="center">
                    {#if d.capitalizedCorrectly === true}
                      <span class="badge badge-success">✓</span>
                    {:else if d.capitalizedCorrectly === false}
                      <span class="badge badge-fail">✗</span>
                    {:else}
                      <span class="dim">—</span>
                    {/if}
                  </td>
                </tr>
              {/each}
            </tbody>
          </table>
        </div>
      {/each}
    {/if}
  {/if}
{/if}

<style>
  .sub-tabs {
    display: flex;
    gap: 0.3rem;
    margin-bottom: 1.25rem;
  }

  .sub-tabs button {
    padding: 0.3rem 0.85rem;
    font-size: 0.75rem;
    font-weight: 600;
    letter-spacing: 0.05em;
    background: transparent;
    color: var(--text-secondary);
    border: 1px solid #2a2a3a;
    border-radius: 4px;
    cursor: pointer;
    transition: all 0.15s;
  }

  .sub-tabs button:hover { color: var(--text-primary); border-color: #555; }
  .sub-tabs button.active { background: #E8002D; color: #fff; border-color: #E8002D; }

  .state-msg {
    padding: 3rem 2rem;
    color: var(--text-secondary);
    text-align: center;
  }
  .state-msg.error { color: #FF5252; }

  .warning {
    background: #1e1a0e;
    border: 1px solid #6b5010;
    color: #c89020;
    padding: 0.6rem 1rem;
    border-radius: 4px;
    font-size: 0.8rem;
    margin-bottom: 1rem;
  }

  .laps-control {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    margin-bottom: 1rem;
    font-size: 0.8rem;
    color: var(--text-secondary);
  }

  .laps-control label { display: flex; align-items: center; gap: 0.4rem; }

  .laps-control input {
    width: 4rem;
    padding: 0.2rem 0.4rem;
    background: var(--bg-secondary);
    color: var(--text-primary);
    border: 1px solid #333;
    border-radius: 3px;
    font-size: 0.8rem;
    text-align: center;
  }

  .laps-control button {
    padding: 0.2rem 0.7rem;
    font-size: 0.75rem;
    font-weight: 600;
    background: transparent;
    color: var(--text-secondary);
    border: 1px solid #333;
    border-radius: 3px;
    cursor: pointer;
    transition: all 0.15s;
  }

  .laps-control button:hover { color: var(--text-primary); border-color: #555; }

  .section-title {
    font-size: 0.78rem;
    font-weight: 700;
    letter-spacing: 0.1em;
    text-transform: uppercase;
    color: var(--text-secondary);
    margin-bottom: 0.75rem;
    padding-bottom: 0.4rem;
    border-bottom: 1px solid #2a2a3a;
  }

  .table-wrap { overflow-x: auto; }

  table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.85rem;
  }

  th, td {
    padding: 0.5rem 0.75rem;
    text-align: left;
    border-bottom: 1px solid #1e1e2e;
    white-space: nowrap;
  }

  th {
    color: var(--text-secondary);
    font-size: 0.72rem;
    font-weight: 600;
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }

  tr:hover td { background: #1a1a2a; }

  .driver-cell { font-weight: 600; }
  .pos { font-weight: 700; }
  .center { text-align: center; }
  .mono { font-family: monospace; }
  .dim { color: var(--text-secondary); }
  .gained { color: #69F0AE; font-weight: 600; font-family: monospace; }

  .dot {
    display: inline-block;
    width: 8px; height: 8px;
    border-radius: 50%;
    margin-right: 0.4rem;
    background: #888;
  }

  .stint-row {
    display: flex;
    align-items: center;
    gap: 0.3rem;
    flex-wrap: wrap;
  }

  .stint-row.alt { opacity: 0.75; }

  .arrow { color: var(--text-secondary); font-size: 0.75rem; }

  .stint-chip {
    display: flex;
    align-items: center;
    gap: 0.25rem;
  }

  .compound-badge {
    display: inline-block;
    padding: 0.1rem 0.4rem;
    border-radius: 3px;
    font-size: 0.72rem;
    font-weight: 700;
    letter-spacing: 0.03em;
  }

  .compound-badge.sm { font-size: 0.68rem; padding: 0.08rem 0.3rem; }

  .stint-laps {
    font-size: 0.75rem;
    color: var(--text-secondary);
    font-family: monospace;
  }

  .pit-window {
    font-size: 0.68rem;
    color: #a0a0c0;
    font-family: monospace;
  }

  /* Comparison cards */
  .comparison-grid {
    display: flex;
    gap: 1rem;
    flex-wrap: wrap;
    margin-bottom: 0.5rem;
  }

  .comparison-card {
    flex: 1;
    min-width: 140px;
    background: var(--bg-secondary);
    border: 1px solid #2a2a3a;
    border-radius: 6px;
    padding: 0.75rem 1rem;
  }

  .comp-label {
    font-size: 0.72rem;
    font-weight: 700;
    letter-spacing: 0.1em;
    text-transform: uppercase;
    color: var(--text-secondary);
    margin-bottom: 0.4rem;
  }

  .comp-count { font-size: 0.85rem; font-weight: 600; }
  .comp-avg   { font-size: 1rem; font-weight: 700; color: #E8002D; }
  .comp-drivers { font-size: 0.75rem; color: var(--text-secondary); margin-top: 0.3rem; }

  /* Badges */
  .badge {
    display: inline-block;
    padding: 0.1rem 0.45rem;
    border-radius: 3px;
    font-size: 0.72rem;
    font-weight: 700;
    letter-spacing: 0.04em;
  }

  .badge-success { background: #0a2e14; color: #69F0AE; border: 1px solid #1a6b30; }
  .badge-fail    { background: #2e0a0a; color: #FF5252; border: 1px solid #6b1a1a; }

  /* Score bar */
  .score-bar {
    display: flex;
    align-items: center;
    gap: 0.4rem;
    min-width: 90px;
  }

  .score-fill {
    height: 6px;
    border-radius: 3px;
    transition: width 0.3s;
    flex: 0 0 auto;
  }

  .score-val {
    font-size: 0.75rem;
    font-family: monospace;
    color: var(--text-secondary);
    min-width: 1.5rem;
  }

  .rec-msg {
    font-size: 0.78rem;
    color: var(--text-secondary);
  }

  @media (max-width: 599px) {
    .comparison-grid { flex-direction: column; }
    .sub-tabs { flex-wrap: wrap; }
    .sub-tabs button { font-size: 0.7rem; }
  }
</style>
