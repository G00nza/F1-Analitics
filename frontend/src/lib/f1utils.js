/**
 * Format a lap time in milliseconds to "m:ss.mmm"
 * @param {number} ms
 * @returns {string}
 */
export function formatLapTime(ms) {
    if (ms == null) return '--:--.---';
    const minutes = Math.floor(ms / 60000);
    const seconds = Math.floor((ms % 60000) / 1000);
    const millis  = ms % 1000;
    return `${minutes}:${String(seconds).padStart(2, '0')}.${String(millis).padStart(3, '0')}`;
}

/**
 * Format a countdown/remaining time in milliseconds to "m:ss" or "h:mm:ss"
 * @param {number} ms
 * @returns {string}
 */
export function formatTimeRemaining(ms) {
    if (ms == null || ms < 0) return null;
    const totalSec = Math.floor(ms / 1000);
    const h = Math.floor(totalSec / 3600);
    const m = Math.floor((totalSec % 3600) / 60);
    const s = totalSec % 60;
    if (h > 0) return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
    return `${m}:${String(s).padStart(2, '0')}`;
}

/**
 * Convert wind direction in degrees to 8-point compass label
 * @param {number} degrees
 * @returns {string}
 */
export function windDirectionLabel(degrees) {
    if (degrees == null) return '';
    const dirs = ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW'];
    return dirs[Math.round(degrees / 45) % 8];
}

/**
 * Format a gap value (ms) as "+s.mmm" or "leader"
 * @param {number|null} gapMs
 * @returns {string}
 */
export function formatGap(gapMs) {
    if (gapMs == null || gapMs === 0) return 'leader';
    const s = (gapMs / 1000).toFixed(3);
    return `+${s}`;
}

/** Team colour map (primary colour) */
const TEAM_COLORS = {
    'Red Bull':    '#3671C6',
    'Ferrari':     '#E8002D',
    'McLaren':     '#FF8000',
    'Mercedes':    '#27F4D2',
    'Aston Martin':'#229971',
    'Alpine':      '#FF87BC',
    'Williams':    '#64C4FF',
    'RB':          '#6692FF',
    'Kick Sauber': '#52E252',
    'Haas':        '#B6BABD',
};

export function teamColor(teamName) {
    return TEAM_COLORS[teamName] ?? '#FFFFFF';
}

/** Tyre compound → CSS variable */
export function tyreColor(compound) {
    const map = {
        SOFT:   'var(--tyre-soft)',
        MEDIUM: 'var(--tyre-medium)',
        HARD:   'var(--tyre-hard)',
        INTER:  'var(--tyre-inter)',
        WET:    'var(--tyre-wet)',
    };
    return map[compound?.toUpperCase()] ?? 'var(--text-secondary)';
}
