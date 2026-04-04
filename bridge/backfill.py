"""
F1 Historical Backfill via FastF1

Loads completed race sessions and writes laps, stints, telemetry, positions,
weather, and race control messages into the local SQLite database.

Usage:
    python3 bridge/backfill.py                  # 2026, all completed rounds
    python3 bridge/backfill.py 2025             # 2025, all rounds
    python3 bridge/backfill.py 2025 3           # 2025, round 3 only
    python3 bridge/backfill.py 2025 all         # 2025, all rounds (explicit)
    python3 bridge/backfill.py 2025 3 --no-telemetry  # skip high-freq car data
"""

import argparse
import logging
import math
import os
import sqlite3
import sys
from datetime import timezone
from pathlib import Path

import fastf1
import pandas as pd

# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [backfill] %(levelname)s %(message)s",
    datefmt="%H:%M:%S",
)
logger = logging.getLogger("backfill")

# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------

SCRIPT_DIR = Path(__file__).parent
DB_PATH    = SCRIPT_DIR.parent / "f1analytics.db"
CACHE_DIR  = SCRIPT_DIR / "cache"

# ---------------------------------------------------------------------------
# Key helpers (must match Kotlin SeasonLoader conventions)
# ---------------------------------------------------------------------------

def race_key(year: int, round_num: int) -> int:
    return year * 10_000 + round_num

def session_key(year: int, round_num: int) -> int:
    return year * 10_000 + round_num + 5_000

# ---------------------------------------------------------------------------
# Conversion helpers
# ---------------------------------------------------------------------------

def _ms(td) -> int | None:
    """Convert a pandas Timedelta / timedelta to integer milliseconds."""
    if td is None:
        return None
    try:
        if pd.isna(td):
            return None
    except (TypeError, ValueError):
        pass
    try:
        return int(td.total_seconds() * 1_000)
    except Exception:
        return None


def _bool(val) -> int | None:
    """Convert pandas bool/NA to 0/1/None for SQLite."""
    try:
        if pd.isna(val):
            return None
    except (TypeError, ValueError):
        pass
    return 1 if val else 0


def _str(val) -> str | None:
    """Convert a value to str, treating NaN/NaT as None."""
    try:
        if pd.isna(val):
            return None
    except (TypeError, ValueError):
        pass
    s = str(val).strip()
    return s if s else None


def _int(val) -> int | None:
    try:
        if pd.isna(val):
            return None
    except (TypeError, ValueError):
        pass
    try:
        return int(val)
    except Exception:
        return None


def _float(val) -> float | None:
    try:
        if pd.isna(val):
            return None
    except (TypeError, ValueError):
        pass
    try:
        f = float(val)
        return None if math.isnan(f) else f
    except Exception:
        return None


def _ts(session_date, offset) -> str | None:
    """
    Compute a SQLite-compatible UTC timestamp from session start + session-relative offset.
    Format: 'YYYY-MM-DD HH:MM:SS.ffffff' (space separator, no timezone suffix).
    """
    try:
        if pd.isna(offset):
            return None
    except (TypeError, ValueError):
        pass
    try:
        dt = session_date + offset
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        dt_utc = dt.astimezone(timezone.utc).replace(tzinfo=None)
        return dt_utc.strftime("%Y-%m-%d %H:%M:%S.%f")
    except Exception:
        return None

# ---------------------------------------------------------------------------
# Main backfill logic
# ---------------------------------------------------------------------------

def backfill_round(con: sqlite3.Connection, year: int, round_num: int, include_telemetry: bool) -> None:
    sk = session_key(year, round_num)

    # Check that the session exists in DB
    row = con.execute("SELECT key, status FROM sessions WHERE key = ?", (sk,)).fetchone()
    if row is None:
        logger.warning("Session key %d not found in DB — run 'f1 data init' first", sk)
        return
    if row[1] != "Finished":
        logger.info("Round %d is not finished yet (status=%s), skipping", round_num, row[1])
        return

    logger.info("Loading FastF1 data for %d round %d…", year, round_num)
    session = fastf1.get_session(year, round_num, "R")
    session.load(laps=True, telemetry=include_telemetry, weather=True, messages=True)
    logger.info("  FastF1 load complete — %d laps", len(session.laps))

    session_date = session.date  # tz-aware start datetime

    with con:
        _clear_session(con, sk)
        _insert_drivers(con, sk, session)
        _insert_laps(con, sk, session, session_date)
        _insert_stints(con, sk, session)
        _insert_weather(con, sk, session, session_date)
        _insert_race_control(con, sk, session, session_date)
        _insert_positions(con, sk, session, session_date)
        if include_telemetry:
            _insert_telemetry(con, sk, session, session_date)

    logger.info("  Round %d backfill complete", round_num)


def _clear_session(con: sqlite3.Connection, sk: int) -> None:
    """Delete all previously backfilled data for this session."""
    for table in ("laps", "stints", "pit_stops", "session_drivers",
                  "weather_snapshots", "race_control_messages",
                  "position_snapshots", "car_telemetry"):
        con.execute(f"DELETE FROM {table} WHERE session_key = ?", (sk,))


def _insert_drivers(con: sqlite3.Connection, sk: int, session) -> None:
    rows = []
    for number in session.drivers:
        info = session.get_driver(number)
        full = info.get("FullName", "") or ""
        parts = full.split(" ", 1)
        first = parts[0] if parts else None
        last  = parts[1] if len(parts) > 1 else None
        rows.append((
            sk,
            str(number),
            _str(info.get("Abbreviation")),
            first or None,
            last or None,
            _str(info.get("TeamName")),
            _str(info.get("TeamColor")),
        ))
    con.executemany(
        "INSERT OR REPLACE INTO session_drivers "
        "(session_key, number, code, first_name, last_name, team, team_color) "
        "VALUES (?, ?, ?, ?, ?, ?, ?)",
        rows,
    )
    logger.info("  Drivers: %d inserted", len(rows))


def _insert_laps(con: sqlite3.Connection, sk: int, session, session_date) -> None:
    laps = session.laps
    rows = []
    for _, lap in laps.iterrows():
        rows.append((
            sk,
            _str(lap["DriverNumber"]),
            _int(lap["LapNumber"]),
            _ms(lap["LapTime"]),
            _ms(lap["Sector1Time"]),
            _ms(lap["Sector2Time"]),
            _ms(lap["Sector3Time"]),
            _bool(lap.get("IsPersonalBest")),
            0,  # is_overall_best — computed separately if needed
            1 if _ms(lap.get("PitOutTime")) is not None else 0,
            1 if _ms(lap.get("PitInTime"))  is not None else 0,
            _ts(session_date, lap["Time"]),
        ))
    con.executemany(
        "INSERT OR IGNORE INTO laps "
        "(session_key, driver_number, lap_number, lap_time_ms, "
        " sector1_ms, sector2_ms, sector3_ms, "
        " is_personal_best, is_overall_best, pit_out_lap, pit_in_lap, timestamp) "
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        rows,
    )
    logger.info("  Laps: %d inserted", len(rows))


def _insert_stints(con: sqlite3.Connection, sk: int, session) -> None:
    laps = session.laps
    if "Stint" not in laps.columns:
        return

    rows = []
    for driver_num in laps["DriverNumber"].unique():
        driver_laps = laps[laps["DriverNumber"] == driver_num].sort_values("LapNumber")
        for stint_num, group in driver_laps.groupby("Stint"):
            first_row = group.iloc[0]
            rows.append((
                sk,
                str(driver_num),
                _int(stint_num),
                _str(first_row.get("Compound")),
                _bool(first_row.get("IsNew")) if "IsNew" in first_row.index else None,
                _int(group["LapNumber"].min()),
                _int(group["LapNumber"].max()),
            ))

    con.executemany(
        "INSERT OR IGNORE INTO stints "
        "(session_key, driver_number, stint_number, compound, is_new, lap_start, lap_end) "
        "VALUES (?, ?, ?, ?, ?, ?, ?)",
        rows,
    )
    logger.info("  Stints: %d inserted", len(rows))


def _insert_weather(con: sqlite3.Connection, sk: int, session, session_date) -> None:
    if session.weather_data is None or session.weather_data.empty:
        return

    wd = session.weather_data
    rows = []
    for _, row in wd.iterrows():
        rows.append((
            sk,
            _ts(session_date, row["Time"]),
            _float(row.get("AirTemp")),
            _float(row.get("TrackTemp")),
            _float(row.get("Humidity")),
            _float(row.get("Pressure")),
            _float(row.get("WindSpeed")),
            _int(row.get("WindDirection")),
            _bool(row.get("Rainfall")),
        ))

    con.executemany(
        "INSERT INTO weather_snapshots "
        "(session_key, timestamp, air_temp, track_temp, humidity, pressure, "
        " wind_speed, wind_direction, rainfall) "
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        rows,
    )
    logger.info("  Weather: %d snapshots inserted", len(rows))


def _abs_ts(val) -> str | None:
    """
    Convert an absolute pandas Timestamp to a SQLite-compatible UTC timestamp.
    Format: 'YYYY-MM-DD HH:MM:SS.ffffff' (space separator, no timezone suffix).
    """
    try:
        if pd.isna(val):
            return None
    except (TypeError, ValueError):
        pass
    try:
        ts = pd.Timestamp(val)
        if ts.tzinfo is None:
            ts = ts.tz_localize("UTC")
        ts_utc = ts.tz_convert("UTC")
        return ts_utc.strftime("%Y-%m-%d %H:%M:%S.%f")
    except Exception:
        return None


def _insert_race_control(con: sqlite3.Connection, sk: int, session, session_date) -> None:
    if session.race_control_messages is None or session.race_control_messages.empty:
        return

    rcm = session.race_control_messages
    rows = []
    for _, row in rcm.iterrows():
        ts = _abs_ts(row["Time"])
        if ts is None:
            continue
        rows.append((
            sk,
            ts,
            _str(row.get("Category")),
            _str(row.get("Message")),
            _str(row.get("Flag")),
            _str(row.get("Scope")),
            _int(row.get("Sector")),
            _str(row.get("RacingNumber")),   # driver_number column in DB
            _int(row.get("Lap")),
        ))

    con.executemany(
        "INSERT INTO race_control_messages "
        "(session_key, timestamp, category, message, flag, scope, sector, driver_number, lap_number) "
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        rows,
    )
    logger.info("  Race control: %d messages inserted", len(rows))


def _insert_positions(con: sqlite3.Connection, sk: int, session, session_date) -> None:
    """Persist race position per driver per completed lap (from laps DataFrame)."""
    laps = session.laps
    if "Position" not in laps.columns:
        return

    rows = []
    for _, lap in laps.iterrows():
        pos = _int(lap.get("Position"))
        if pos is None:
            continue
        rows.append((
            sk,
            _ts(session_date, lap["Time"]),
            _str(lap["DriverNumber"]),
            pos,
            _str(lap.get("GapToLeader")),
            _str(lap.get("IntervalToPositionAhead")),
        ))

    con.executemany(
        "INSERT INTO position_snapshots "
        "(session_key, timestamp, driver_number, position, gap_to_leader, interval) "
        "VALUES (?, ?, ?, ?, ?, ?)",
        rows,
    )
    logger.info("  Positions: %d snapshots inserted", len(rows))


def _insert_telemetry(con: sqlite3.Connection, sk: int, session, session_date) -> None:
    """Insert high-frequency car telemetry. Chunked to avoid memory pressure."""
    laps = session.laps
    total = 0
    for driver_num in laps["DriverNumber"].unique():
        driver_laps = laps.pick_drivers(str(driver_num))
        try:
            tel = driver_laps.get_telemetry()
        except Exception as exc:
            logger.warning("  No telemetry for driver %s: %s", driver_num, exc)
            continue

        rows = []
        for _, row in tel.iterrows():
            rows.append((
                sk,
                _ts(session_date, row.get("SessionTime")),
                str(driver_num),
                _int(row.get("Speed")),
                _int(row.get("RPM")),
                _int(row.get("nGear")),
                _int(row.get("Throttle")),
                _int(row.get("Brake")),
                _int(row.get("DRS")),
            ))

        con.executemany(
            "INSERT INTO car_telemetry "
            "(session_key, timestamp, driver_number, speed, rpm, gear, throttle, brake, drs) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            rows,
        )
        total += len(rows)

    logger.info("  Telemetry: %d rows inserted", total)


# ---------------------------------------------------------------------------
# Completed rounds discovery
# ---------------------------------------------------------------------------

def completed_rounds(con: sqlite3.Connection, year: int) -> list[int]:
    rows = con.execute(
        "SELECT r.round FROM races r "
        "JOIN sessions s ON s.race_key = r.key "
        "WHERE r.year = ? AND s.status = 'Finished' AND s.type = 'RACE' "
        "ORDER BY r.round",
        (year,),
    ).fetchall()
    return [r[0] for r in rows]


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(description="Backfill F1 historical session data")
    parser.add_argument("year", nargs="?", type=int, default=None,
                        help="Season year (default: current from DB)")
    parser.add_argument("round", nargs="?", default=None,
                        help="Round number or 'all' (default: all completed)")
    parser.add_argument("--no-telemetry", dest="telemetry", action="store_false",
                        help="Skip high-frequency car telemetry (faster)")
    parser.set_defaults(telemetry=True)
    args = parser.parse_args()

    # FastF1 cache
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    fastf1.Cache.enable_cache(str(CACHE_DIR))

    con = sqlite3.connect(str(DB_PATH))

    # Determine year
    if args.year is None:
        from datetime import date
        args.year = date.today().year

    year = args.year

    # Determine rounds
    if args.round is None or str(args.round).lower() == "all":
        rounds = completed_rounds(con, year)
        if not rounds:
            logger.info("No completed rounds found for %d", year)
            con.close()
            return
        logger.info("Backfilling %d completed rounds for %d: %s", len(rounds), year, rounds)
    else:
        rounds = [int(args.round)]

    for r in rounds:
        try:
            backfill_round(con, year, r, include_telemetry=args.telemetry)
        except Exception as exc:
            logger.error("Failed to backfill round %d: %s", r, exc, exc_info=True)

    con.close()
    logger.info("Backfill complete")


if __name__ == "__main__":
    main()
