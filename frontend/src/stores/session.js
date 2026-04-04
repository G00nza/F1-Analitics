import { writable, derived } from 'svelte/store';

export const connectionStatus = writable('connecting'); // 'connecting' | 'connected' | 'reconnecting'
export const nextSession = writable(null); // SessionStartingSoonDto | null

// Raw DTO from backend (LiveSessionStateDto)
const _rawState = writable(null);

/**
 * Normalized session state consumed by components.
 * Merges the separate `drivers` and `driverData` maps into a flat,
 * position-sorted array so components don't need to join them.
 */
export const sessionState = derived(_rawState, ($raw) => {
    if (!$raw) return null;

    const drivers = Object.entries($raw.driverData ?? {})
        .map(([num, data]) => {
            const entry = $raw.drivers?.[num] ?? {};
            const stintLaps =
                data.lapNumber != null && data.stintLapStart != null
                    ? data.lapNumber - data.stintLapStart + 1
                    : null;
            return {
                driverNumber: num,
                driverCode: entry.code ?? num,
                firstName: entry.firstName ?? null,
                lastName: entry.lastName ?? null,
                teamName: entry.team ?? '',
                teamColor: entry.teamColor ?? '#FFFFFF',
                position: data.position ?? 999,
                gapToLeader: data.gapToLeader ?? null,   // string | null (null = leader)
                bestLapMs: data.bestLapTimeMs ?? null,
                lastLapMs: data.lastLapTimeMs ?? null,
                lastS1: data.sector1Ms ?? null,
                lastS2: data.sector2Ms ?? null,
                lastS3: data.sector3Ms ?? null,
                inPit: data.inPit ?? false,
                currentCompound: data.currentCompound ?? null,
                currentStintLaps: stintLaps,
            };
        })
        .sort((a, b) => a.position - b.position);

    return {
        sessionKey: $raw.sessionKey,
        sessionName: $raw.sessionName ?? null,
        circuitName: $raw.circuitName ?? null,
        officialName: $raw.officialName ?? null,
        sessionStatus: $raw.sessionStatus ?? null,
        trackStatus: $raw.trackStatus ?? null,
        timeRemainingMs: $raw.timeRemainingMs ?? null,
        lapCount: $raw.lapCount ?? null,
        weather: $raw.weather ?? null,
        drivers,
        raceControlMessages: $raw.raceControlMessages ?? [],
    };
});

function attachHandlers(source) {
    source.addEventListener('session_state', (e) => {
        _rawState.set(JSON.parse(e.data));
    });
    source.addEventListener('session_starting_soon', (e) => {
        nextSession.set(JSON.parse(e.data));
    });
    source.onerror = () => connectionStatus.set('reconnecting');
    source.onopen  = () => connectionStatus.set('connected');
}

let _es = new EventSource('/api/events/live');
attachHandlers(_es);

/** Switch to a recorded replay stream for the given session key. */
export function connectToReplay(sessionKey) {
    _es.close();
    connectionStatus.set('connecting');
    _rawState.set(null);
    _es = new EventSource(`/api/events/replay/${sessionKey}`);
    attachHandlers(_es);
}

/** Switch back to the live stream. */
export function connectToLive() {
    _es.close();
    connectionStatus.set('connecting');
    _rawState.set(null);
    _es = new EventSource('/api/events/live');
    attachHandlers(_es);
}
