import { writable } from 'svelte/store';

/**
 * The race key the user has selected in the header selector.
 * null  → backend uses findCurrent() (default)
 * number → specific race key
 */
export const selectedRaceKey = writable(null);
