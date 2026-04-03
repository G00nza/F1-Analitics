package com.f1analytics.data.livetiming

import com.f1analytics.core.domain.model.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Instant
import kotlinx.serialization.json.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

/**
 * Parses raw JSON from the F1 bridge into typed [TimingMessage] instances.
 *
 * F1 live timing sends partial (delta) updates — only changed fields are included.
 * Unknown topics and unparseable payloads are logged as WARN and return null.
 */
internal object TimingMessageParser {

    fun parse(topic: String, data: JsonElement, timestamp: Instant): TimingMessage? = try {
        when (topic) {
            "SessionInfo"         -> parseSessionInfo(data)
            "SessionStatus"       -> parseSessionStatus(data)
            "DriverList"          -> parseDriverList(data)
            "TimingData"          -> parseTimingData(data)
            "TimingAppData"       -> parseTimingAppData(data)
            "TrackStatus"         -> parseTrackStatus(data)
            "RaceControlMessages" -> parseRaceControlMessages(data)
            "WeatherData"         -> parseWeatherData(data)
            "CarData.z"           -> parseCarData(data)
            "Position.z"          -> parsePosition(data)
            "ExtrapolatedClock"   -> parseExtrapolatedClock(data)
            "LapCount"            -> parseLapCount(data)
            "Heartbeat"           -> parseHeartbeat(data)
            "TimingStats"         -> null  // not used
            else -> {
                logger.debug { "Ignoring unknown topic: $topic" }
                null
            }
        }
    } catch (e: Exception) {
        logger.warn { "Failed to parse topic '$topic': ${e.message}" }
        null
    }

    // ── Topic parsers ──────────────────────────────────────────────────────

    private fun parseSessionInfo(data: JsonElement): TimingMessage.SessionInfoMsg? {
        val obj = data.jsonObject
        val meeting = obj["Meeting"]?.jsonObject ?: return null
        val name = obj["Name"]?.jsonPrimitive?.contentOrNull ?: return null
        val sessionType = obj["Type"]?.jsonPrimitive?.contentOrNull ?: name
        val circuit = meeting["Circuit"]?.jsonObject?.get("ShortName")?.jsonPrimitive?.contentOrNull
            ?: meeting["Name"]?.jsonPrimitive?.contentOrNull
            ?: return null
        return TimingMessage.SessionInfoMsg(
            name = name,
            circuit = circuit,
            type = SessionType.from(sessionType),
            officialName = meeting["OfficialName"]?.jsonPrimitive?.contentOrNull
        )
    }

    private fun parseSessionStatus(data: JsonElement): TimingMessage.SessionStatusMsg {
        val status = data.jsonObject["Status"]?.jsonPrimitive?.contentOrNull ?: "Unknown"
        return TimingMessage.SessionStatusMsg(status)
    }

    private fun parseDriverList(data: JsonElement): TimingMessage.DriverListMsg {
        val drivers = buildMap {
            for ((number, entry) in data.jsonObject) {
                if (entry !is JsonObject) continue
                val code = entry["Tla"]?.jsonPrimitive?.contentOrNull ?: continue
                put(
                    number, DriverEntry(
                        number = number,
                        code = code,
                        firstName = entry["FirstName"]?.jsonPrimitive?.contentOrNull,
                        lastName = entry["LastName"]?.jsonPrimitive?.contentOrNull,
                        team = entry["TeamName"]?.jsonPrimitive?.contentOrNull,
                        teamColor = entry["TeamColour"]?.jsonPrimitive?.contentOrNull
                    )
                )
            }
        }
        return TimingMessage.DriverListMsg(drivers)
    }

    private fun parseTimingData(data: JsonElement): TimingMessage.TimingDataMsg? {
        val lines = data.jsonObject["Lines"]?.jsonObject ?: return null
        val deltas = buildMap {
            for ((driverNum, lineData) in lines) {
                if (lineData !is JsonObject) continue
                parseDriverTimingDelta(lineData)?.let { put(driverNum, it) }
            }
        }
        return if (deltas.isEmpty()) null else TimingMessage.TimingDataMsg(deltas)
    }

    private fun parseDriverTimingDelta(obj: JsonObject): DriverTimingDelta? {
        val position = obj["Position"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        val gapToLeader = obj["GapToLeader"]?.jsonPrimitive?.contentOrNull
        val interval = obj["IntervalToPositionAhead"].asStringOrNull()
        val lapNumber = obj["NumberOfLaps"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        val inPit = obj["InPit"]?.jsonPrimitive?.asBooleanOrNull()
        val pitOut = obj["PitOut"]?.jsonPrimitive?.asBooleanOrNull()

        val lastLapObj = obj["LastLapTime"]?.jsonObject
        val lastLapTimeMs = lastLapObj?.get("Value")?.jsonPrimitive?.contentOrNull?.let(::parseLapTimeToMs)
        val lastLapPersonalBest = lastLapObj?.get("PersonalFastest")?.jsonPrimitive?.asBooleanOrNull()
        val lastLapOverallBest = lastLapObj?.get("OverallFastest")?.jsonPrimitive?.asBooleanOrNull()

        val sectors = obj["Sectors"]?.jsonObject
        val sector1Ms = sectors?.get("0")?.jsonObject?.get("Value")?.jsonPrimitive?.contentOrNull?.let(::parseSectorTimeToMs)
        val sector2Ms = sectors?.get("1")?.jsonObject?.get("Value")?.jsonPrimitive?.contentOrNull?.let(::parseSectorTimeToMs)
        val sector3Ms = sectors?.get("2")?.jsonObject?.get("Value")?.jsonPrimitive?.contentOrNull?.let(::parseSectorTimeToMs)

        // Skip empty deltas with no useful data
        if (position == null && gapToLeader == null && lastLapTimeMs == null &&
            lapNumber == null && sector1Ms == null && sector2Ms == null && sector3Ms == null
        ) return null

        return DriverTimingDelta(
            position = position,
            gapToLeader = gapToLeader,
            interval = interval,
            lastLapTimeMs = lastLapTimeMs,
            lastLapPersonalBest = lastLapPersonalBest,
            lastLapOverallBest = lastLapOverallBest,
            sector1Ms = sector1Ms,
            sector2Ms = sector2Ms,
            sector3Ms = sector3Ms,
            lapNumber = lapNumber,
            inPit = inPit,
            pitOut = pitOut
        )
    }

    private fun parseTimingAppData(data: JsonElement): TimingMessage.TimingAppDataMsg? {
        val lines = data.jsonObject["Lines"]?.jsonObject ?: return null
        val deltas = buildMap {
            for ((driverNum, lineData) in lines) {
                val stints = (lineData as? JsonObject)?.get("Stints")?.jsonObject ?: continue
                // Take the stint with the highest index (current stint)
                val (stintNum, stintObj) = stints.entries
                    .mapNotNull { (k, v) -> k.toIntOrNull()?.let { Pair(it, v as? JsonObject) } }
                    .maxByOrNull { it.first } ?: continue
                stintObj ?: continue

                val compound = stintObj["Compound"]?.jsonPrimitive?.contentOrNull
                val totalLaps = stintObj["TotalLaps"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                if (compound == null && totalLaps == null) continue

                put(
                    driverNum, DriverTireStintDelta(
                        stintNumber = stintNum,
                        compound = compound,
                        isNew = stintObj["New"]?.jsonPrimitive?.asBooleanOrNull(),
                        lapStart = stintObj["StartLaps"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                        totalLaps = totalLaps
                    )
                )
            }
        }
        return if (deltas.isEmpty()) null else TimingMessage.TimingAppDataMsg(deltas)
    }

    private fun parseTrackStatus(data: JsonElement): TimingMessage.TrackStatusMsg {
        val obj = data.jsonObject
        return TimingMessage.TrackStatusMsg(
            status = obj["Status"]?.jsonPrimitive?.contentOrNull ?: "Unknown",
            message = obj["Message"]?.jsonPrimitive?.contentOrNull ?: ""
        )
    }

    private fun parseRaceControlMessages(data: JsonElement): TimingMessage.RaceControlMsg? {
        val messages = data.jsonObject["Messages"]?.jsonObject ?: return null
        // Take the message with the highest index key (most recent in the batch)
        val msgObj = messages.entries
            .mapNotNull { (k, v) -> k.toIntOrNull()?.let { Pair(it, v as? JsonObject) } }
            .maxByOrNull { it.first }?.second ?: return null
        return TimingMessage.RaceControlMsg(
            message = msgObj["Message"]?.jsonPrimitive?.contentOrNull ?: return null,
            flag = msgObj["Flag"]?.jsonPrimitive?.contentOrNull,
            scope = msgObj["Scope"]?.jsonPrimitive?.contentOrNull,
            lap = msgObj["Lap"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        )
    }

    private fun parseWeatherData(data: JsonElement): TimingMessage.WeatherMsg {
        val obj = data.jsonObject
        return TimingMessage.WeatherMsg(
            WeatherData(
                airTemp = obj["AirTemp"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull(),
                trackTemp = obj["TrackTemp"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull(),
                humidity = obj["Humidity"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull(),
                pressure = obj["Pressure"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull(),
                windSpeed = obj["WindSpeed"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull(),
                windDirection = obj["WindDirection"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                rainfall = obj["Rainfall"]?.jsonPrimitive?.asBooleanOrNull()
            )
        )
    }

    private fun parseCarData(data: JsonElement): TimingMessage.CarDataMsg? {
        val entriesArray = data.jsonObject["Entries"]?.jsonArray ?: return null
        val entries = buildList {
            for (entry in entriesArray) {
                val entryObj = entry as? JsonObject ?: continue
                val utcStr = entryObj["Utc"]?.jsonPrimitive?.contentOrNull ?: continue
                val timestamp = runCatching { Instant.parse(utcStr) }.getOrNull() ?: continue
                val cars = entryObj["Cars"]?.jsonObject ?: continue
                for ((driverNum, carData) in cars) {
                    val channels = (carData as? JsonObject)?.get("Channels")?.jsonObject ?: continue
                    add(
                        CarTelemetryEntry(
                            timestamp = timestamp,
                            driverNumber = driverNum,
                            rpm = channels["0"]?.jsonPrimitive?.intOrNull,
                            gear = channels["2"]?.jsonPrimitive?.intOrNull,
                            speed = channels["3"]?.jsonPrimitive?.intOrNull,
                            throttle = channels["4"]?.jsonPrimitive?.intOrNull,
                            brake = channels["5"]?.jsonPrimitive?.intOrNull,
                            drs = channels["45"]?.jsonPrimitive?.intOrNull
                        )
                    )
                }
            }
        }
        return if (entries.isEmpty()) null else TimingMessage.CarDataMsg(entries)
    }

    private fun parsePosition(data: JsonElement): TimingMessage.PositionMsg? {
        val positionArray = data.jsonObject["Position"]?.jsonArray ?: return null
        val entries = buildList {
            for (snapshot in positionArray) {
                val snapshotObj = snapshot as? JsonObject ?: continue
                val tsStr = snapshotObj["Timestamp"]?.jsonPrimitive?.contentOrNull ?: continue
                val timestamp = runCatching { Instant.parse(tsStr) }.getOrNull() ?: continue
                val driverEntries = snapshotObj["Entries"]?.jsonObject ?: continue
                for ((driverNum, pos) in driverEntries) {
                    val posObj = pos as? JsonObject ?: continue
                    add(
                        PositionEntry(
                            timestamp = timestamp,
                            driverNumber = driverNum,
                            status = posObj["Status"]?.jsonPrimitive?.contentOrNull,
                            x = posObj["X"]?.jsonPrimitive?.intOrNull,
                            y = posObj["Y"]?.jsonPrimitive?.intOrNull,
                            z = posObj["Z"]?.jsonPrimitive?.intOrNull
                        )
                    )
                }
            }
        }
        return if (entries.isEmpty()) null else TimingMessage.PositionMsg(entries)
    }

    private fun parseExtrapolatedClock(data: JsonElement): TimingMessage.ExtrapolatedClockMsg {
        val obj = data.jsonObject
        return TimingMessage.ExtrapolatedClockMsg(
            remaining = obj["Remaining"]?.jsonPrimitive?.contentOrNull?.let(::parseDuration),
            extrapolating = obj["Extrapolating"]?.jsonPrimitive?.asBooleanOrNull() ?: false
        )
    }

    private fun parseLapCount(data: JsonElement): TimingMessage.LapCountMsg? {
        val obj = data.jsonObject
        val current = obj["CurrentLap"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return null
        return TimingMessage.LapCountMsg(
            current = current,
            total = obj["TotalLaps"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        )
    }

    private fun parseHeartbeat(data: JsonElement): TimingMessage.HeartbeatMsg? {
        val utcStr = data.jsonObject["Utc"]?.jsonPrimitive?.contentOrNull ?: return null
        val instant = runCatching { Instant.parse(utcStr) }.getOrNull() ?: return null
        return TimingMessage.HeartbeatMsg(instant)
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /** Parse "1:29.432" or "29.432" to milliseconds. Returns null for blank/invalid. */
    internal fun parseLapTimeToMs(value: String): Int? {
        if (value.isBlank()) return null
        return runCatching {
            if (':' in value) {
                val (minPart, secPart) = value.split(":", limit = 2)
                ((minPart.toInt() * 60 + secPart.toDouble()) * 1000).toInt()
            } else {
                (value.toDouble() * 1000).toInt()
            }
        }.getOrNull()
    }

    /** Parse "25.123" sector time to milliseconds. Returns null for blank/invalid. */
    internal fun parseSectorTimeToMs(value: String): Int? {
        if (value.isBlank()) return null
        return runCatching { (value.toDouble() * 1000).toInt() }.getOrNull()
    }

    /** Parse "H:MM:SS" or "MM:SS" duration string. */
    internal fun parseDuration(value: String): Duration? {
        if (value.isBlank()) return null
        return runCatching {
            val parts = value.split(":").map { it.toInt() }
            when (parts.size) {
                3 -> parts[0].hours + parts[1].minutes + parts[2].seconds
                2 -> parts[0].minutes + parts[1].seconds
                1 -> parts[0].seconds
                else -> null
            }
        }.getOrNull()
    }

    /** Extract a string value from either a JsonPrimitive or {"Value": "..."} object. */
    private fun JsonElement?.asStringOrNull(): String? = when (this) {
        is JsonObject    -> this["Value"]?.jsonPrimitive?.contentOrNull
        is JsonPrimitive -> contentOrNull
        else             -> null
    }

    /** Boolean that handles both JSON booleans and string "TRUE"/"FALSE" (F1 quirk). */
    private fun JsonPrimitive.asBooleanOrNull(): Boolean? =
        booleanOrNull ?: when (contentOrNull?.uppercase()) {
            "TRUE"  -> true
            "FALSE" -> false
            else    -> null
        }
}
