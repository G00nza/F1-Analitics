package com.f1analytics.data.livetiming

import com.f1analytics.core.domain.model.*
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlin.test.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TimingMessageParserTest {

    private val ts = Instant.parse("2024-03-02T16:00:00Z")

    private fun parse(topic: String, dataJson: String): TimingMessage? =
        TimingMessageParser.parse(topic, Json.parseToJsonElement(dataJson), ts)

    // ── SessionInfo ────────────────────────────────────────────────────────

    @Test
    fun `parses SessionInfo`() {
        val msg = parse(
            "SessionInfo", """
            {
              "Name": "Qualifying",
              "Type": "Qualifying",
              "Meeting": {
                "Name": "Bahrain Grand Prix",
                "OfficialName": "FORMULA 1 GULF AIR BAHRAIN GRAND PRIX 2024",
                "Circuit": { "ShortName": "Bahrain" }
              }
            }
        """
        ) as TimingMessage.SessionInfoMsg

        assertEquals("Qualifying", msg.name)
        assertEquals("Bahrain", msg.circuit)
        assertEquals(SessionType.QUALIFYING, msg.type)
        assertEquals("FORMULA 1 GULF AIR BAHRAIN GRAND PRIX 2024", msg.officialName)
    }

    @Test
    fun `parses SessionInfo with unknown type as UNKNOWN`() {
        val msg = parse(
            "SessionInfo", """
            {
              "Name": "Shootout",
              "Type": "Sprint Shootout",
              "Meeting": { "Circuit": { "ShortName": "Spa" } }
            }
        """
        ) as TimingMessage.SessionInfoMsg
        assertEquals(SessionType.UNKNOWN, msg.type)
    }

    // ── SessionStatus ──────────────────────────────────────────────────────

    @Test
    fun `parses SessionStatus`() {
        val msg = parse("SessionStatus", """{"Status": "Started"}""") as TimingMessage.SessionStatusMsg
        assertEquals("Started", msg.status)
    }

    // ── DriverList ─────────────────────────────────────────────────────────

    @Test
    fun `parses DriverList`() {
        val msg = parse(
            "DriverList", """
            {
              "1":  { "Tla": "VER", "FirstName": "Max",    "LastName": "Verstappen", "TeamName": "Red Bull Racing", "TeamColour": "3671C6" },
              "44": { "Tla": "HAM", "FirstName": "Lewis",  "LastName": "Hamilton",   "TeamName": "Mercedes",        "TeamColour": "27F4D2" }
            }
        """
        ) as TimingMessage.DriverListMsg

        assertEquals(2, msg.drivers.size)
        val ver = msg.drivers["1"]!!
        assertEquals("VER", ver.code)
        assertEquals("Max", ver.firstName)
        assertEquals("Red Bull Racing", ver.team)
        assertEquals("3671C6", ver.teamColor)
    }

    @Test
    fun `DriverList ignores entries without Tla`() {
        val msg = parse("DriverList", """{"1": {"FirstName": "Max"}, "44": {"Tla": "HAM"}}""") as TimingMessage.DriverListMsg
        assertEquals(1, msg.drivers.size)
        assertNotNull(msg.drivers["44"])
    }

    // ── TimingData ─────────────────────────────────────────────────────────

    @Test
    fun `parses TimingData with lap time and sectors`() {
        val msg = parse(
            "TimingData", """
            {
              "Lines": {
                "1": {
                  "Position": "1",
                  "GapToLeader": "",
                  "IntervalToPositionAhead": {"Value": ""},
                  "LastLapTime": {"Value": "1:29.432", "PersonalFastest": false, "OverallFastest": true},
                  "Sectors": {
                    "0": {"Value": "25.123"},
                    "1": {"Value": "35.456"},
                    "2": {"Value": "28.853"}
                  },
                  "NumberOfLaps": 10,
                  "InPit": false,
                  "PitOut": false
                }
              }
            }
        """
        ) as TimingMessage.TimingDataMsg

        val delta = msg.deltas["1"]!!
        assertEquals(1, delta.position)
        assertEquals(89432, delta.lastLapTimeMs)   // 1:29.432
        assertFalse(delta.lastLapPersonalBest!!)
        assertTrue(delta.lastLapOverallBest!!)
        assertEquals(25123, delta.sector1Ms)
        assertEquals(35456, delta.sector2Ms)
        assertEquals(28853, delta.sector3Ms)
        assertEquals(10, delta.lapNumber)
        assertFalse(delta.inPit!!)
    }

    @Test
    fun `parses TimingData with interval as plain string`() {
        val msg = parse(
            "TimingData", """{"Lines": {"33": {"Position": "3", "GapToLeader": "+5.200", "IntervalToPositionAhead": "+1.100", "NumberOfLaps": 5}}}"""
        ) as TimingMessage.TimingDataMsg

        val delta = msg.deltas["33"]!!
        assertEquals("+5.200", delta.gapToLeader)
        assertEquals("+1.100", delta.interval)
    }

    @Test
    fun `TimingData returns null when Lines is empty`() {
        val msg = parse("TimingData", """{"Lines": {}}""")
        assertNull(msg)
    }

    @Test
    fun `TimingData skips deltas with no useful fields`() {
        val msg = parse("TimingData", """{"Lines": {"1": {"Retired": false}}}""")
        assertNull(msg)
    }

    // ── TimingAppData ──────────────────────────────────────────────────────

    @Test
    fun `parses TimingAppData stint info`() {
        val msg = parse(
            "TimingAppData", """
            {
              "Lines": {
                "16": {
                  "Stints": {
                    "0": {"Compound": "SOFT", "New": "TRUE", "StartLaps": 0, "TotalLaps": 12}
                  }
                }
              }
            }
        """
        ) as TimingMessage.TimingAppDataMsg

        val delta = msg.deltas["16"]!!
        assertEquals(0, delta.stintNumber)
        assertEquals("SOFT", delta.compound)
        assertTrue(delta.isNew!!)
        assertEquals(0, delta.lapStart)
        assertEquals(12, delta.totalLaps)
    }

    @Test
    fun `TimingAppData picks highest stint index as current`() {
        val msg = parse(
            "TimingAppData", """
            {
              "Lines": {
                "4": {
                  "Stints": {
                    "0": {"Compound": "SOFT", "TotalLaps": 20},
                    "1": {"Compound": "MEDIUM", "TotalLaps": 5}
                  }
                }
              }
            }
        """
        ) as TimingMessage.TimingAppDataMsg

        assertEquals("MEDIUM", msg.deltas["4"]!!.compound)
        assertEquals(1, msg.deltas["4"]!!.stintNumber)
    }

    // ── TrackStatus ────────────────────────────────────────────────────────

    @Test
    fun `parses TrackStatus`() {
        val msg = parse("TrackStatus", """{"Status": "4", "Message": "SafetyCar"}""") as TimingMessage.TrackStatusMsg
        assertEquals("4", msg.status)
        assertEquals("SafetyCar", msg.message)
    }

    // ── RaceControlMessages ────────────────────────────────────────────────

    @Test
    fun `parses RaceControlMessages`() {
        val msg = parse(
            "RaceControlMessages", """
            {
              "Messages": {
                "1": {"Message": "GREEN FLAG", "Flag": "GREEN", "Scope": "Track", "Lap": 1}
              }
            }
        """
        ) as TimingMessage.RaceControlMsg

        assertEquals("GREEN FLAG", msg.message)
        assertEquals("GREEN", msg.flag)
        assertEquals("Track", msg.scope)
        assertEquals(1, msg.lap)
    }

    @Test
    fun `RaceControlMessages picks highest key when multiple in batch`() {
        val msg = parse(
            "RaceControlMessages", """
            {
              "Messages": {
                "5": {"Message": "YELLOW FLAG", "Flag": "YELLOW"},
                "6": {"Message": "GREEN FLAG",  "Flag": "GREEN"}
              }
            }
        """
        ) as TimingMessage.RaceControlMsg
        assertEquals("GREEN FLAG", msg.message)
    }

    // ── WeatherData ────────────────────────────────────────────────────────

    @Test
    fun `parses WeatherData`() {
        val msg = parse(
            "WeatherData", """
            {"AirTemp": "23.3", "TrackTemp": "34.5", "Humidity": "68", "Pressure": "1013.5",
             "WindSpeed": "1.3", "WindDirection": "182", "Rainfall": false}
        """
        ) as TimingMessage.WeatherMsg

        assertEquals(23.3, msg.weather.airTemp)
        assertEquals(34.5, msg.weather.trackTemp)
        assertEquals(68.0, msg.weather.humidity)
        assertFalse(msg.weather.rainfall!!)
    }

    @Test
    fun `parses WeatherData with string boolean rainfall`() {
        val msg = parse("WeatherData", """{"Rainfall": "True", "AirTemp": "20.0"}""") as TimingMessage.WeatherMsg
        assertTrue(msg.weather.rainfall!!)
    }

    // ── CarData.z ─────────────────────────────────────────────────────────

    @Test
    fun `parses CarData`() {
        val msg = parse(
            "CarData.z", """
            {
              "Entries": [{
                "Utc": "2024-03-02T16:00:00Z",
                "Cars": {
                  "1": {"Channels": {"0": 9090, "2": 3, "3": 250, "4": 95, "5": 0, "45": 10}}
                }
              }]
            }
        """
        ) as TimingMessage.CarDataMsg

        assertEquals(1, msg.entries.size)
        val entry = msg.entries[0]
        assertEquals("1", entry.driverNumber)
        assertEquals(9090, entry.rpm)
        assertEquals(3, entry.gear)
        assertEquals(250, entry.speed)
        assertEquals(95, entry.throttle)
        assertEquals(0, entry.brake)
        assertEquals(10, entry.drs)
    }

    @Test
    fun `CarData with multiple cars and timestamps`() {
        val msg = parse(
            "CarData.z", """
            {
              "Entries": [
                {"Utc": "2024-03-02T16:00:00Z", "Cars": {"1": {"Channels": {"3": 200}}, "44": {"Channels": {"3": 180}}}},
                {"Utc": "2024-03-02T16:00:01Z", "Cars": {"1": {"Channels": {"3": 210}}}}
              ]
            }
        """
        ) as TimingMessage.CarDataMsg
        assertEquals(3, msg.entries.size)
    }

    // ── Position.z ────────────────────────────────────────────────────────

    @Test
    fun `parses Position`() {
        val msg = parse(
            "Position.z", """
            {
              "Position": [{
                "Timestamp": "2024-03-02T16:00:00Z",
                "Entries": {
                  "1":  {"Status": "OnTrack", "X": 12345, "Y": 6789, "Z": 0},
                  "44": {"Status": "OnTrack", "X": 11000, "Y": 6500, "Z": 0}
                }
              }]
            }
        """
        ) as TimingMessage.PositionMsg

        assertEquals(2, msg.entries.size)
        val pos = msg.entries.first { it.driverNumber == "1" }
        assertEquals("OnTrack", pos.status)
        assertEquals(12345, pos.x)
        assertEquals(6789, pos.y)
        assertEquals(0, pos.z)
    }

    // ── ExtrapolatedClock ─────────────────────────────────────────────────

    @Test
    fun `parses ExtrapolatedClock`() {
        val msg = parse(
            "ExtrapolatedClock", """{"Remaining": "1:30:00", "Extrapolating": false}"""
        ) as TimingMessage.ExtrapolatedClockMsg

        assertEquals(1.hours + 30.minutes, msg.remaining)
        assertFalse(msg.extrapolating)
    }

    @Test
    fun `parses ExtrapolatedClock with MM-SS remaining`() {
        val msg = parse(
            "ExtrapolatedClock", """{"Remaining": "45:30", "Extrapolating": true}"""
        ) as TimingMessage.ExtrapolatedClockMsg

        assertEquals(45.minutes + 30.seconds, msg.remaining)
        assertTrue(msg.extrapolating)
    }

    // ── LapCount ──────────────────────────────────────────────────────────

    @Test
    fun `parses LapCount`() {
        val msg = parse("LapCount", """{"CurrentLap": 15, "TotalLaps": 57}""") as TimingMessage.LapCountMsg
        assertEquals(15, msg.current)
        assertEquals(57, msg.total)
    }

    @Test
    fun `parses LapCount without TotalLaps (qualifying)`() {
        val msg = parse("LapCount", """{"CurrentLap": 3}""") as TimingMessage.LapCountMsg
        assertEquals(3, msg.current)
        assertNull(msg.total)
    }

    // ── Heartbeat ─────────────────────────────────────────────────────────

    @Test
    fun `parses Heartbeat`() {
        val msg = parse("Heartbeat", """{"Utc": "2024-03-02T16:05:00Z"}""") as TimingMessage.HeartbeatMsg
        assertEquals(Instant.parse("2024-03-02T16:05:00Z"), msg.utcTime)
    }

    // ── Error handling ────────────────────────────────────────────────────

    @Test
    fun `unknown topic returns null without throwing`() {
        val msg = parse("UnknownTopic", """{"foo": "bar"}""")
        assertNull(msg)
    }

    @Test
    fun `malformed JSON data returns null without throwing`() {
        // Passes valid JSON but wrong structure — should return null, not throw
        val msg = parse("SessionInfo", """{"unexpected": true}""")
        assertNull(msg)
    }

    @Test
    fun `TimingStats returns null`() {
        val msg = parse("TimingStats", """{"Lines": {}}""")
        assertNull(msg)
    }

    // ── Lap time parsing helpers ──────────────────────────────────────────

    @Test
    fun `parseLapTimeToMs handles M-SS-mmm format`() {
        assertEquals(89432, TimingMessageParser.parseLapTimeToMs("1:29.432"))
    }

    @Test
    fun `parseLapTimeToMs handles SS-mmm format`() {
        assertEquals(29432, TimingMessageParser.parseLapTimeToMs("29.432"))
    }

    @Test
    fun `parseLapTimeToMs returns null for blank string`() {
        assertNull(TimingMessageParser.parseLapTimeToMs(""))
        assertNull(TimingMessageParser.parseLapTimeToMs("  "))
    }

    @Test
    fun `parseSectorTimeToMs converts correctly`() {
        assertEquals(25123, TimingMessageParser.parseSectorTimeToMs("25.123"))
    }

    @Test
    fun `parseDuration handles H-MM-SS format`() {
        assertEquals(1.hours + 30.minutes + 0.seconds, TimingMessageParser.parseDuration("1:30:00"))
    }
}
