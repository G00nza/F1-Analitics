"""
Backfill gap_to_leader and interval in position_snapshots from laps data.

For each position_snapshot row, we derive:
  - gap_to_leader : time gap to the race leader at that instant
  - interval      : time gap to the car directly ahead

Gap calculation uses the lap-completion clock timestamps directly:
  gap = driver's lap-N finish time − leader's lap-N finish time

This avoids the cumulative-sum approach, which breaks whenever a driver has
NULL lap_time_ms entries (common for the leader), causing an artificially low
cumulative total that inflates gaps for everyone else.

Leader identification: most laps completed; ties broken by earliest lap
completion timestamp (NOT cumulative time).

Format:
  - Same-lap gap  : "+X.XXX"  (seconds, 3 decimal places)
  - Lapped car    : "+N LAP" / "+N LAPS"
  - Leader        : NULL (no gap)

Usage:
    python3 bridge/backfill_gaps.py                     # all sessions with NULL gaps
    python3 bridge/backfill_gaps.py --session 20260005  # single session
    python3 bridge/backfill_gaps.py --force             # overwrite existing values too
"""

import argparse
import logging
import sqlite3
from datetime import datetime
from pathlib import Path

# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [backfill_gaps] %(levelname)s %(message)s",
    datefmt="%H:%M:%S",
)
logger = logging.getLogger("backfill_gaps")

# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------

DB_PATH = Path(__file__).parent.parent / "f1analytics.db"

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _format_gap(gap_ms: int) -> str:
    return f"+{gap_ms / 1000:.3f}"


def _format_lap_gap(laps: int) -> str:
    return f"+{laps} LAP{'S' if laps > 1 else ''}"


def _ts_diff_ms(ts_later: str, ts_earlier: str) -> int | None:
    """
    Return (ts_later − ts_earlier) in whole milliseconds.
    Handles ISO-8601 strings with or without the 'T' separator and 'Z' suffix.
    Returns None if either timestamp is missing.
    """
    if ts_later is None or ts_earlier is None:
        return None

    def _parse(ts: str) -> datetime:
        return datetime.fromisoformat(ts.replace("Z", "+00:00").replace(" ", "T"))

    return int((_parse(ts_later) - _parse(ts_earlier)).total_seconds() * 1000)


# ---------------------------------------------------------------------------
# Core logic
# ---------------------------------------------------------------------------

def backfill_session(con: sqlite3.Connection, session_key: int, force: bool) -> int:
    """
    Compute and update gap_to_leader / interval for one session.
    Returns the number of rows updated.

    Approach:
      1. Cross-join position_snapshots × laps to find every driver's latest
         completed lap at each snapshot timestamp.
      2. Rank drivers by (most laps DESC, earliest lap_ts ASC) to identify
         the leader and the car directly ahead.
      3. For same-lap gaps, return both the driver's and the reference car's
         lap-completion timestamps; Python computes the clock-time difference.
      4. Batch-UPDATE position_snapshots.
    """

    snap_filter = "" if force else "AND (ps.gap_to_leader IS NULL OR ps.interval IS NULL)"

    sql = f"""
    WITH
    -- All laps that have a recorded completion time
    valid_laps AS (
        SELECT driver_number,
               lap_number,
               timestamp AS lap_ts
        FROM   laps
        WHERE  session_key = ?
               AND lap_time_ms IS NOT NULL
    ),
    -- For every (snapshot × any-driver): the highest lap number that driver
    -- had completed by the snapshot timestamp.
    snap_driver_max_lap AS (
        SELECT ps.id            AS snap_id,
               ps.timestamp     AS snap_ts,
               ps.driver_number AS snap_driver,
               vl.driver_number,
               MAX(vl.lap_number) AS lap_number
        FROM   position_snapshots ps
        JOIN   valid_laps vl ON vl.lap_ts <= ps.timestamp
        WHERE  ps.session_key = ?
               {snap_filter}
        GROUP  BY ps.id, ps.timestamp, ps.driver_number, vl.driver_number
    ),
    -- Attach the completion timestamp for each (driver, max_lap) pair
    snap_driver_full AS (
        SELECT sdm.snap_id,
               sdm.snap_driver,
               sdm.driver_number,
               sdm.lap_number,
               vl.lap_ts
        FROM   snap_driver_max_lap sdm
        JOIN   valid_laps vl
               ON  vl.driver_number = sdm.driver_number
               AND vl.lap_number    = sdm.lap_number
    ),
    -- Rank every driver per snapshot:
    --   P1 = most laps, ties broken by earliest lap-completion timestamp.
    ranked AS (
        SELECT *,
               ROW_NUMBER() OVER (
                   PARTITION BY snap_id
                   ORDER BY lap_number DESC, lap_ts ASC
               ) AS race_pos
        FROM snap_driver_full
    ),
    -- Snapshot's own driver row
    self_ranked AS (
        SELECT * FROM ranked WHERE driver_number = snap_driver
    ),
    -- Leader row (race_pos = 1) per snapshot
    leader AS (
        SELECT snap_id,
               driver_number AS leader_driver,
               lap_number    AS leader_lap
        FROM   ranked
        WHERE  race_pos = 1
    ),
    -- Car directly ahead of the snapshot driver
    car_ahead AS (
        SELECT sr.snap_id,
               r.driver_number AS ahead_driver,
               r.lap_number    AS ahead_lap
        FROM   self_ranked sr
        JOIN   ranked r
               ON  r.snap_id  = sr.snap_id
               AND r.race_pos = sr.race_pos - 1
    ),
    -- Leader's lap-completion timestamp for the SAME lap the driver is on.
    -- Used to compute clock-time gap when both are on the same lap.
    leader_lap_ts AS (
        SELECT sr.snap_id,
               vl.lap_ts AS ts
        FROM   self_ranked sr
        JOIN   leader l  ON l.snap_id = sr.snap_id
        JOIN   valid_laps vl
               ON  vl.driver_number = l.leader_driver
               AND vl.lap_number    = sr.lap_number
    ),
    -- Car-ahead's lap-completion timestamp for the same lap.
    ahead_lap_ts AS (
        SELECT sr.snap_id,
               vl.lap_ts AS ts
        FROM   self_ranked sr
        JOIN   car_ahead ca ON ca.snap_id = sr.snap_id
        JOIN   valid_laps vl
               ON  vl.driver_number = ca.ahead_driver
               AND vl.lap_number    = sr.lap_number
    )
    SELECT sr.snap_id,
           sr.lap_number    AS this_lap,
           sr.lap_ts        AS this_lap_ts,
           l.leader_lap,
           llt.ts           AS leader_lap_ts,
           ca.ahead_lap,
           alt.ts           AS ahead_lap_ts
    FROM      self_ranked   sr
    JOIN      leader        l   ON  l.snap_id  = sr.snap_id
    LEFT JOIN car_ahead     ca  ON  ca.snap_id = sr.snap_id
    LEFT JOIN leader_lap_ts llt ON  llt.snap_id = sr.snap_id
    LEFT JOIN ahead_lap_ts  alt ON  alt.snap_id = sr.snap_id
    """

    rows = con.execute(sql, (session_key, session_key)).fetchall()

    if not rows:
        logger.info("  Session %d: nothing to update", session_key)
        return 0

    updates: list[tuple[str | None, str | None, int]] = []

    for (snap_id, this_lap, this_lap_ts,
         leader_lap, leader_lap_ts,
         ahead_lap,  ahead_lap_ts) in rows:

        # --- gap_to_leader ---------------------------------------------------
        lap_diff = leader_lap - this_lap
        if lap_diff == 0:
            gap_ms = _ts_diff_ms(this_lap_ts, leader_lap_ts)
            gap_to_leader: str | None = _format_gap(gap_ms) if gap_ms and gap_ms > 0 else None
        elif lap_diff > 0:
            gap_to_leader = _format_lap_gap(lap_diff)
        else:
            gap_to_leader = None  # shouldn't happen

        # --- interval --------------------------------------------------------
        interval: str | None
        if ahead_lap is None:
            interval = None  # this driver is the leader
        elif ahead_lap == this_lap:
            gap_ms = _ts_diff_ms(this_lap_ts, ahead_lap_ts)
            interval = _format_gap(max(gap_ms or 0, 0))
        else:
            interval = _format_lap_gap(ahead_lap - this_lap)

        updates.append((gap_to_leader, interval, snap_id))

    con.executemany(
        "UPDATE position_snapshots SET gap_to_leader = ?, interval = ? WHERE id = ?",
        updates,
    )
    con.commit()

    logger.info("  Session %d: updated %d rows", session_key, len(updates))
    return len(updates)


# ---------------------------------------------------------------------------
# Session discovery
# ---------------------------------------------------------------------------

def sessions_with_null_gaps(con: sqlite3.Connection) -> list[int]:
    rows = con.execute(
        """
        SELECT DISTINCT session_key
        FROM position_snapshots
        WHERE gap_to_leader IS NULL OR interval IS NULL
        ORDER BY session_key
        """
    ).fetchall()
    return [r[0] for r in rows]


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(description="Backfill gap_to_leader and interval")
    parser.add_argument(
        "--session", type=int, default=None,
        help="Process a single session_key (default: all with NULL gaps)",
    )
    parser.add_argument(
        "--force", action="store_true",
        help="Overwrite existing non-NULL values (default: skip already-filled rows)",
    )
    args = parser.parse_args()

    con = sqlite3.connect(str(DB_PATH))

    if args.session:
        session_keys = [args.session]
    else:
        session_keys = sessions_with_null_gaps(con)
        if not session_keys:
            logger.info("No sessions with NULL gaps found.")
            con.close()
            return
        logger.info("Found %d session(s) to process: %s", len(session_keys), session_keys)

    total = 0
    for sk in session_keys:
        try:
            total += backfill_session(con, sk, force=args.force)
        except Exception as exc:
            logger.error("Session %d failed: %s", sk, exc, exc_info=True)

    con.close()
    logger.info("Done — %d total rows updated", total)


if __name__ == "__main__":
    main()
