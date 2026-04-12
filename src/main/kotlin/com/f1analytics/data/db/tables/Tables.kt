package com.f1analytics.data.db.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

// ── Meetings & sessions ────────────────────────────────────────────────────

object RacesTable : Table("races") {
    val key          = integer("key")
    val name         = text("name")
    val officialName = text("official_name").nullable()
    val circuit      = text("circuit")
    val country      = text("country").nullable()
    val year         = integer("year")
    val round        = integer("round").nullable()
    val dateStart    = text("date_start").nullable()   // DATE stored as ISO text
    val dateEnd      = text("date_end").nullable()
    override val primaryKey = PrimaryKey(key)
}

object SessionsTable : Table("sessions") {
    val key       = integer("key")
    val raceKey   = integer("race_key").references(RacesTable.key, onDelete = ReferenceOption.SET_NULL).nullable()
    val name      = text("name")
    val type      = text("type")               // FP1|FP2|FP3|QUALIFYING|SPRINT|RACE…
    val year      = integer("year")            // denormalized for idx_sessions_year_type
    val status    = text("status").nullable()  // Started|Finished|Aborted
    val dateStart = timestamp("date_start").nullable()
    val dateEnd   = timestamp("date_end").nullable()
    val recorded  = bool("recorded").default(false)
    override val primaryKey = PrimaryKey(key)

    init {
        index("idx_sessions_year_type", isUnique = false, year, type)
    }
}

// ── Drivers per session ────────────────────────────────────────────────────

object SessionDriversTable : Table("session_drivers") {
    val sessionKey = integer("session_key").references(SessionsTable.key)
    val number     = text("number")
    val code       = text("code")
    val firstName  = text("first_name").nullable()
    val lastName   = text("last_name").nullable()
    val team       = text("team").nullable()
    val teamColor  = text("team_color").nullable()
    override val primaryKey = PrimaryKey(sessionKey, number)
}

// ── Laps ───────────────────────────────────────────────────────────────────

object LapsTable : Table("laps") {
    val id            = integer("id").autoIncrement()
    val sessionKey    = integer("session_key").references(SessionsTable.key)
    val driverNumber  = text("driver_number")
    val lapNumber     = integer("lap_number")
    val lapTimeMs     = integer("lap_time_ms").nullable()
    val sector1Ms     = integer("sector1_ms").nullable()
    val sector2Ms     = integer("sector2_ms").nullable()
    val sector3Ms     = integer("sector3_ms").nullable()
    val isPersonalBest = bool("is_personal_best").default(false)
    val isOverallBest  = bool("is_overall_best").default(false)
    val pitOutLap      = bool("pit_out_lap").default(false)
    val pitInLap       = bool("pit_in_lap").default(false)
    val timestamp      = timestamp("timestamp")
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("laps_unique_session_driver_lap", sessionKey, driverNumber, lapNumber)
        index("idx_laps_session_driver", isUnique = false, sessionKey, driverNumber)
        index("idx_laps_session_lap",    isUnique = false, sessionKey, lapNumber)
    }
}

// ── Tyre stints ────────────────────────────────────────────────────────────

object StintsTable : Table("stints") {
    val id           = integer("id").autoIncrement()
    val sessionKey   = integer("session_key").references(SessionsTable.key)
    val driverNumber = text("driver_number")
    val stintNumber  = integer("stint_number")
    val compound     = text("compound").nullable()   // SOFT|MEDIUM|HARD|INTERMEDIATE|WET
    val isNew        = bool("is_new").nullable()
    val lapStart     = integer("lap_start").nullable()
    val lapEnd       = integer("lap_end").nullable()  // NULL = active stint
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("stints_unique_session_driver_stint", sessionKey, driverNumber, stintNumber)
        index("idx_stints_session_driver", isUnique = false, sessionKey, driverNumber)
    }
}

// ── Pit stops ──────────────────────────────────────────────────────────────

object PitStopsTable : Table("pit_stops") {
    val id           = integer("id").autoIncrement()
    val sessionKey   = integer("session_key").references(SessionsTable.key)
    val driverNumber = text("driver_number")
    val lapNumber    = integer("lap_number").nullable()
    val timestamp    = timestamp("timestamp")
    override val primaryKey = PrimaryKey(id)
}

// ── Race control messages ──────────────────────────────────────────────────

object RaceControlMessagesTable : Table("race_control_messages") {
    val id           = integer("id").autoIncrement()
    val sessionKey   = integer("session_key").references(SessionsTable.key)
    val timestamp    = timestamp("timestamp")
    val category     = text("category").nullable()
    val message      = text("message")
    val flag         = text("flag").nullable()
    val scope        = text("scope").nullable()
    val sector       = integer("sector").nullable()
    val driverNumber = text("driver_number").nullable()
    val lapNumber    = integer("lap_number").nullable()
    override val primaryKey = PrimaryKey(id)
}

// ── Weather ────────────────────────────────────────────────────────────────

object WeatherSnapshotsTable : Table("weather_snapshots") {
    val id            = integer("id").autoIncrement()
    val sessionKey    = integer("session_key").references(SessionsTable.key)
    val timestamp     = timestamp("timestamp")
    val airTemp       = double("air_temp").nullable()
    val trackTemp     = double("track_temp").nullable()
    val humidity      = double("humidity").nullable()
    val pressure      = double("pressure").nullable()
    val windSpeed     = double("wind_speed").nullable()
    val windDirection = integer("wind_direction").nullable()
    val rainfall      = bool("rainfall").nullable()
    override val primaryKey = PrimaryKey(id)
}

// ── Position & gap snapshots (from TimingData) ─────────────────────────────

object PositionSnapshotsTable : Table("position_snapshots") {
    val id           = integer("id").autoIncrement()
    val sessionKey   = integer("session_key").references(SessionsTable.key)
    val timestamp    = timestamp("timestamp")
    val driverNumber = text("driver_number")
    val position     = integer("position").nullable()
    val gapToLeader  = text("gap_to_leader").nullable()
    val interval     = text("interval").nullable()
    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_pos_session_ts", isUnique = false, sessionKey, timestamp)
    }
}

// ── Car telemetry (high-frequency, optional) ───────────────────────────────

object CarTelemetryTable : Table("car_telemetry") {
    val id           = integer("id").autoIncrement()
    val sessionKey   = integer("session_key").references(SessionsTable.key)
    val timestamp    = timestamp("timestamp")
    val driverNumber = text("driver_number")
    val speed        = integer("speed").nullable()
    val rpm          = integer("rpm").nullable()
    val gear         = integer("gear").nullable()
    val throttle     = integer("throttle").nullable()
    val brake        = integer("brake").nullable()
    val drs          = integer("drs").nullable()
    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_telemetry_session_ts", isUnique = false, sessionKey, timestamp)
    }
}

// ── Historical / backfill ──────────────────────────────────────────────────

object RaceResultsTable : Table("race_results") {
    val id              = integer("id").autoIncrement()
    val sessionKey      = integer("session_key").references(SessionsTable.key)
    val driverCode      = text("driver_code")
    val constructorName = text("constructor_name").nullable()
    val gridPosition    = integer("grid_position").nullable()
    val finishPosition  = integer("finish_position").nullable()
    val points          = double("points").nullable()
    val status          = text("status").nullable()
    val fastestLap      = bool("fastest_lap").default(false)
    val lapsCompleted   = integer("laps_completed").nullable()
    override val primaryKey = PrimaryKey(id)
}

object DriverStandingsTable : Table("driver_standings") {
    val id          = integer("id").autoIncrement()
    val year        = integer("year")
    val afterRound  = integer("after_round")
    val driverCode  = text("driver_code")
    val position    = integer("position").nullable()
    val points      = double("points").nullable()
    val wins        = integer("wins").nullable()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("standings_unique_year_round_driver", year, afterRound, driverCode)
    }
}

// ── Settings (key-value store) ─────────────────────────────────────────────

object SettingsTable : Table("settings") {
    val key   = text("key")
    val value = text("value")
    override val primaryKey = PrimaryKey(key)
}

// ── Strategy alerts (undercut/overcut) ────────────────────────────────────

object StrategyAlertsTable : Table("strategy_alerts") {
    val id               = integer("id").autoIncrement()
    val sessionKey       = integer("session_key").references(SessionsTable.key)
    val lap              = integer("lap").nullable()
    val type             = text("type")               // UNDERCUT | OVERCUT
    val instigatorNumber = text("instigator_number")
    val rivalNumber      = text("rival_number")
    val gapSeconds       = double("gap_seconds").nullable()
    val predictedOutcome = text("predicted_outcome").nullable()
    val confirmedOutcome = text("confirmed_outcome").nullable()
    val timestamp        = timestamp("timestamp")
    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_strategy_alerts_session", isUnique = false, sessionKey)
    }
}
