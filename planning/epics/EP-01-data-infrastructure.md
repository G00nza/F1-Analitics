# EP-01: Infraestructura de Datos

**Iteración**: 0 — bloqueante para todo
**Prioridad**: Crítica
**Estado**: Pendiente

## Descripción
Tres componentes que trabajan juntos:
1. **bridge.py** — conecta al live timing oficial de F1 y re-publica via WebSocket local
2. **LiveTimingPersistenceService** — persiste cada mensaje a SQLite antes de procesarlo
3. **Repositorios** — acceso a datos desde la DB para analytics y API

La DB es la fuente de verdad. Todo dato que entra se almacena permanentemente.

---

## Componente 1: Python Bridge (`bridge/bridge.py`)

Script Python que usa FastF1 para conectarse al live timing de F1 y re-publicar los mensajes via WebSocket.

```python
# bridge/bridge.py
import asyncio, json, sys, websockets
from fastf1.livetiming.client import SignalRClient

TOPICS = [
    "SessionInfo", "SessionStatus", "DriverList",
    "TimingData", "TimingAppData", "TimingStats",
    "TrackStatus", "RaceControlMessages", "WeatherData",
    "ExtrapolatedClock", "CarData.z", "Position.z",
    "LapCount", "Heartbeat"
]

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 9001
connected: set = set()

async def broadcast(msg: str):
    dead = set()
    for ws in connected.copy():
        try:
            await ws.send(msg)
        except websockets.ConnectionClosed:
            dead.add(ws)
    connected.difference_update(dead)

async def ws_handler(websocket):
    connected.add(websocket)
    try:
        await websocket.wait_closed()
    finally:
        connected.discard(websocket)

def on_message(topic, data, timestamp):
    msg = json.dumps({"topic": topic, "data": data,
                      "timestamp": timestamp.isoformat()})
    asyncio.get_event_loop().call_soon_threadsafe(
        asyncio.ensure_future, broadcast(msg))

async def main():
    f1 = SignalRClient(topics=TOPICS)
    f1.on_message = on_message
    async with websockets.serve(ws_handler, "localhost", PORT):
        print(f"F1 Bridge running on ws://localhost:{PORT}", flush=True)
        await f1.connect()

asyncio.run(main())
```

**Requerimientos:** `bridge/requirements.txt`
```
fastf1>=3.3.0
websockets>=12.0
```

**Criterios de aceptación:**
- [x] Reconexión automática si el F1 timing server cierra la conexión (~2h)
- [x] Múltiples clientes Kotlin conectados al mismo tiempo
- [x] Termina limpiamente con `SIGTERM`
- [x] `CarData.z` y `Position.z` ya llegan descomprimidos (FastF1 lo hace internamente)

---

## Componente 2: LiveTimingWsClient (Kotlin)

Conecta al bridge Python y emite mensajes como tipos del dominio.

```kotlin
// data/livetiming/KtorLiveTimingWsClient.kt
class KtorLiveTimingWsClient(private val httpClient: HttpClient) : LiveTimingClient {

    private val _messages = MutableSharedFlow<TimingMessage>(
        extraBufferCapacity = 2000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val messages: Flow<TimingMessage> = _messages.asSharedFlow()

    override suspend fun connect(port: Int) {
        while (true) {
            try {
                httpClient.webSocket("ws://localhost:$port") {
                    logger.info { "Connected to bridge on port $port" }
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            parseMessage(frame.readText())?.let { _messages.emit(it) }
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warn { "Bridge connection lost: ${e.message}. Retrying in 3s." }
                delay(3.seconds)
            }
        }
    }
}
```

**Sealed class de mensajes:**
```kotlin
sealed class TimingMessage {
    data class SessionInfoMsg(val name: String, val circuit: String,
                               val type: SessionType, val officialName: String?) : TimingMessage()
    data class SessionStatusMsg(val status: String) : TimingMessage()
    data class DriverListMsg(val drivers: Map<String, DriverEntry>) : TimingMessage()
    data class TimingDataMsg(val deltas: Map<String, DriverTimingDelta>) : TimingMessage()
    data class TimingAppDataMsg(val deltas: Map<String, DriverTireStintDelta>) : TimingMessage()
    data class TrackStatusMsg(val status: String, val message: String) : TimingMessage()
    data class RaceControlMsg(val message: String, val flag: String?,
                               val scope: String?, val lap: Int?) : TimingMessage()
    data class WeatherMsg(val weather: WeatherData) : TimingMessage()
    data class CarDataMsg(val entries: List<CarTelemetryEntry>) : TimingMessage()
    data class PositionMsg(val entries: List<PositionEntry>) : TimingMessage()
    data class ExtrapolatedClockMsg(val remaining: Duration?, val extrapolating: Boolean) : TimingMessage()
    data class LapCountMsg(val current: Int, val total: Int?) : TimingMessage()
    data class HeartbeatMsg(val utcTime: Instant) : TimingMessage()
}
```

**Criterios de aceptación:**
- [x] Reconexión automática con retry cada 3s
- [x] Buffer de 2000 mensajes — si la DB es lenta, no se pierden mensajes
- [x] Mensajes no parseables se logean como WARN y se descartan (no crashean)

---

## Componente 3: LiveTimingPersistenceService (Kotlin)

Intercepta cada mensaje y lo persiste en SQLite. Es el componente que garantiza que **ningún dato se pierde**.

```kotlin
class LiveTimingPersistenceService(
    private val sessionRepo: SessionRepository,
    private val driverRepo: SessionDriverRepository,
    private val lapRepo: LapRepository,
    private val stintRepo: StintRepository,
    private val pitRepo: PitStopRepository,
    private val raceControlRepo: RaceControlRepository,
    private val weatherRepo: WeatherRepository,
    private val positionRepo: PositionRepository,
    private val telemetryRepo: TelemetryRepository,
    private val config: AppConfig
) {
    suspend fun persist(sessionKey: Int, msg: TimingMessage, timestamp: Instant) {
        try {
            when (msg) {
                is DriverListMsg     -> driverRepo.upsertAll(sessionKey, msg.drivers)
                is TimingDataMsg     -> {
                    lapRepo.upsertDeltas(sessionKey, msg.deltas, timestamp)
                    positionRepo.insertSnapshot(sessionKey, msg.deltas, timestamp)
                }
                is TimingAppDataMsg  -> stintRepo.upsertDeltas(sessionKey, msg.deltas)
                is RaceControlMsg    -> raceControlRepo.insert(sessionKey, msg, timestamp)
                is WeatherMsg        -> weatherRepo.insert(sessionKey, msg.weather, timestamp)
                is CarDataMsg        -> telemetryRepo.insertBatch(sessionKey, msg.entries)
                is SessionStatusMsg  -> sessionRepo.updateStatus(sessionKey, msg.status)
                else -> { /* SessionInfo y otros se manejan al inicializar la sesión */ }
            }
        } catch (e: Exception) {
            logger.error { "Failed to persist message ${msg::class.simpleName}: ${e.message}" }
            // No relanzar — un error de DB no debe matar el servidor
        }
    }
}
```

**Criterios de aceptación:**
- [ ] Cada mensaje se escribe a DB ANTES de llegar al StateManager
- [ ] Error de escritura → se logea pero no interrumpe el flujo
- [ ] Upsert correcto en laps: los sectores pueden llegar en mensajes separados del mismo lap
- [ ] Telemetría activada por defecto (`config.storeTelemetry = true`)

---

## Componente 4: Esquema SQLite completo

```sql
-- Meetings y sesiones
CREATE TABLE races (
    key           INTEGER PRIMARY KEY,
    name          TEXT NOT NULL,
    official_name TEXT,
    circuit       TEXT NOT NULL,
    country       TEXT,
    year          INTEGER NOT NULL,
    round         INTEGER,
    date_start    DATE,
    date_end      DATE
);

CREATE TABLE sessions (
    key          INTEGER PRIMARY KEY,
    race_key     INTEGER REFERENCES races(key),
    name         TEXT NOT NULL,
    type         TEXT NOT NULL,     -- FP1|FP2|FP3|QUALIFYING|SPRINT|RACE
    status       TEXT,              -- Started|Finished|Aborted
    date_start   DATETIME,
    date_end     DATETIME,
    recorded     BOOLEAN DEFAULT 0  -- 1=capturado en vivo, 0=backfill
);

-- Pilotos por sesión
CREATE TABLE session_drivers (
    session_key  INTEGER REFERENCES sessions(key),
    number       TEXT NOT NULL,
    code         TEXT NOT NULL,
    first_name   TEXT, last_name TEXT,
    team         TEXT, team_color TEXT,
    PRIMARY KEY (session_key, number)
);

-- Vueltas
CREATE TABLE laps (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    session_key      INTEGER REFERENCES sessions(key),
    driver_number    TEXT NOT NULL,
    lap_number       INTEGER NOT NULL,
    lap_time_ms      INTEGER,
    sector1_ms       INTEGER,
    sector2_ms       INTEGER,
    sector3_ms       INTEGER,
    is_personal_best BOOLEAN DEFAULT 0,
    is_overall_best  BOOLEAN DEFAULT 0,
    pit_out_lap      BOOLEAN DEFAULT 0,
    pit_in_lap       BOOLEAN DEFAULT 0,
    timestamp        DATETIME NOT NULL,
    UNIQUE(session_key, driver_number, lap_number)
);

-- Stints de neumáticos
CREATE TABLE stints (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    session_key   INTEGER REFERENCES sessions(key),
    driver_number TEXT NOT NULL,
    stint_number  INTEGER NOT NULL,
    compound      TEXT,            -- SOFT|MEDIUM|HARD|INTERMEDIATE|WET
    is_new        BOOLEAN,
    lap_start     INTEGER,
    lap_end       INTEGER,         -- NULL = stint activo
    UNIQUE(session_key, driver_number, stint_number)
);

-- Pit stops
CREATE TABLE pit_stops (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    session_key   INTEGER REFERENCES sessions(key),
    driver_number TEXT NOT NULL,
    lap_number    INTEGER,
    timestamp     DATETIME NOT NULL
);

-- Mensajes de race control
CREATE TABLE race_control_messages (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    session_key   INTEGER REFERENCES sessions(key),
    timestamp     DATETIME NOT NULL,
    category      TEXT,
    message       TEXT NOT NULL,
    flag          TEXT,
    scope         TEXT,
    sector        INTEGER,
    driver_number TEXT,
    lap_number    INTEGER
);

-- Clima
CREATE TABLE weather_snapshots (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    session_key    INTEGER REFERENCES sessions(key),
    timestamp      DATETIME NOT NULL,
    air_temp       REAL, track_temp REAL,
    humidity       REAL, pressure REAL,
    wind_speed     REAL, wind_direction INTEGER,
    rainfall       BOOLEAN
);

-- Posiciones y gaps (cada cambio significativo)
CREATE TABLE position_snapshots (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    session_key   INTEGER REFERENCES sessions(key),
    timestamp     DATETIME NOT NULL,
    driver_number TEXT NOT NULL,
    position      INTEGER,
    gap_to_leader TEXT,
    interval      TEXT
);

-- Telemetría (opcional, alta frecuencia)
CREATE TABLE car_telemetry (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    session_key   INTEGER REFERENCES sessions(key),
    timestamp     DATETIME NOT NULL,
    driver_number TEXT NOT NULL,
    speed INTEGER, rpm INTEGER, gear INTEGER,
    throttle INTEGER, brake INTEGER, drs INTEGER
);

-- Resultados de carrera (backfill desde Jolpica-F1)
CREATE TABLE race_results (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    session_key      INTEGER REFERENCES sessions(key),
    driver_code      TEXT NOT NULL,
    constructor_name TEXT,
    grid_position    INTEGER,
    finish_position  INTEGER,
    points           REAL,
    status           TEXT,
    fastest_lap      BOOLEAN DEFAULT 0,
    laps_completed   INTEGER
);

-- Standings (backfill o calculado local)
CREATE TABLE driver_standings (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    year         INTEGER NOT NULL,
    after_round  INTEGER NOT NULL,
    driver_code  TEXT NOT NULL,
    position     INTEGER, points REAL, wins INTEGER,
    UNIQUE(year, after_round, driver_code)
);

-- Índices
CREATE INDEX idx_laps_session_driver   ON laps(session_key, driver_number);
CREATE INDEX idx_laps_session_lap      ON laps(session_key, lap_number);
CREATE INDEX idx_stints_session_driver ON stints(session_key, driver_number);
CREATE INDEX idx_pos_session_ts        ON position_snapshots(session_key, timestamp);
CREATE INDEX idx_telemetry_session_ts  ON car_telemetry(session_key, timestamp);
CREATE INDEX idx_sessions_year_type    ON sessions(year, type);
```

---

## Componente 5: Repositorios (interfaces)

```kotlin
interface LapRepository {
    suspend fun upsertDeltas(sessionKey: Int, deltas: Map<String, DriverTimingDelta>, ts: Instant)
    suspend fun findBySession(sessionKey: Int): List<Lap>
    suspend fun findByDriver(sessionKey: Int, driverNumber: String): List<Lap>
    suspend fun findBestLaps(sessionKey: Int): Map<String, Lap>  // best per driver
}

interface StintRepository {
    suspend fun upsertDeltas(sessionKey: Int, deltas: Map<String, DriverTireStintDelta>)
    suspend fun findBySession(sessionKey: Int): List<Stint>
    suspend fun findCurrentStint(sessionKey: Int, driverNumber: String): Stint?
}

interface SessionRepository {
    suspend fun upsert(session: Session)
    suspend fun findByKey(key: Int): Session?
    suspend fun findByYearAndType(year: Int, type: SessionType): List<Session>
    suspend fun findLastRecorded(): Session?
    suspend fun updateStatus(key: Int, status: String)
}

// + PitStopRepository, RaceControlRepository, WeatherRepository,
//   PositionRepository, TelemetryRepository, RaceResultRepository
```

---

## Componente 6: JolpicaClient (CLI only — no runtime)

Usado exclusivamente desde los comandos CLI (`f1 data init`, `f1 data sync`). El servidor en ejecución nunca llama a Jolpica.

```kotlin
interface HistoricalDataClient {
    suspend fun getRaceResults(year: Int, round: Int): List<RaceResultData>
    suspend fun getDriverStandings(year: Int): List<DriverStandingData>
    suspend fun getConstructorStandings(year: Int): List<ConstructorStandingData>
    suspend fun getSeasonSchedule(year: Int): List<RaceScheduleEntry>
}
```

**Base URL**: `https://api.jolpi.ca/ergast/f1/`
**Cache**: resultados de temporadas pasadas tienen TTL infinito; temporada actual TTL 1h.
**Uso típico**:
- Primera ejecución: `f1 data init` → llama a `getSeasonSchedule` + `getDriverStandings` y persiste en DB
- Backfill de carrera perdida: `f1 data sync --round N` → llama a `getRaceResults` y actualiza standings

---

## Gestión del bridge como subprocess

```kotlin
class BridgeProcess(private val port: Int = 9001) {
    private var process: Process? = null

    fun start() {
        process = ProcessBuilder("python3", "bridge/bridge.py", port.toString())
            .redirectErrorStream(true)
            .start()
        Thread { process!!.inputStream.bufferedReader()
            .lines().forEach { logger.info { "[bridge] $it" } } }.start()
        logger.info { "Bridge process started (PID: ${process!!.pid()})" }
    }

    fun stop() {
        process?.destroy()
        logger.info { "Bridge process stopped" }
    }

    fun isAlive(): Boolean = process?.isAlive == true
}
```

---

## Estimación de esfuerzo
- bridge.py + test manual contra F1 timing: 1 día
- LiveTimingWsClient + sealed class de mensajes: 1.5 días
- Esquema SQLite + migraciones Exposed: 1.5 días
- LiveTimingPersistenceService + repositorios: 2 días
- JolpicaClient: 1 día
- BridgeProcess (subprocess management): 0.5 días
- **Total**: ~7.5 días
