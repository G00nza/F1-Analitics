# Arquitectura Técnica

## Principio central
**La base de datos local es la fuente de verdad.** Todo dato que entra del bridge se persiste inmediatamente en SQLite. El StateManager lee desde la DB al arrancar. Los análisis leen desde la DB, no desde APIs remotas.

El servidor puede estar apagado semanas — al volver, todos los datos históricos están disponibles localmente.

---

## Estructura de módulos

```
F1-Analitics/
├── bridge/                     ← Python: F1 live timing → WebSocket local
│   ├── bridge.py
│   └── requirements.txt
│
├── core/                       ← Modelos de dominio e interfaces
│   └── src/main/kotlin/domain/
│       ├── model/
│       └── port/               ← LiveTimingClient, SessionRepository, LapRepository…
│
├── data/                       ← I/O: bridge WS, Jolpica HTTP, SQLite
│   └── src/main/kotlin/
│       ├── livetiming/         ← KtorLiveTimingWsClient
│       ├── historical/         ← KtorJolpicaClient (backfill de sesiones perdidas)
│       └── db/
│           ├── schema/         ← Tablas Exposed
│           ├── repository/     ← SessionRepo, LapRepo, StintRepo…
│           └── LiveTimingPersistenceService.kt  ← escribe cada mensaje del bridge
│
├── analytics/                  ← Lógica de análisis (lee desde repositorios)
│   └── src/main/kotlin/
│       ├── live/               ← LiveSessionStateManager
│       ├── weekend/            ← WeekendAnalysisService
│       ├── strategy/           ← StrategyService
│       ├── qualifying/         ← QualifyingAnalysisService
│       └── season/             ← StandingsService, ChampionshipSimulator
│
├── server/                     ← Ktor: SSE + REST + static files
├── frontend/                   ← Svelte + Vite (IT-0: leaderboard; IT-1+: charts)
└── app/                        ← Entry point, wiring, BridgeProcess
```

---

## Flujo de datos completo

```
┌──────────────────────────────────────────────────────────────┐
│  livetiming.formula1.com (SignalR, público, sin auth)        │
└──────────────────────────┬───────────────────────────────────┘
                           │
                     bridge.py
                  (FastF1 SignalRClient)
                           │ WebSocket localhost:9001
                           │ { topic, data, timestamp }
                           ▼
              KtorLiveTimingWsClient
                           │ Flow<TimingMessage>
                           ▼
         LiveTimingPersistenceService          ← ESCRIBE A DB
                  │                                   │
                  │ (mismos mensajes)           SQLite (local)
                  ▼                                   │
       LiveSessionStateManager  ←────────────────────┘
       (al arrancar, carga estado   lee DB para reconstruir
        de la última sesión)        el estado inicial
                  │
           StateFlow<LiveSessionState>
                  │
          ┌───────┴────────┐
          ▼                ▼
      SseManager      REST endpoints
    (fan-out SSE)   (snapshots, análisis)
          │                │
          ▼                ▼
       Browser          Browser
```

**Flujo de backfill** (sesiones perdidas mientras el servidor estaba apagado):
```
Jolpica-F1 API ──► BackfillService ──► SQLite
OpenF1 API     ──► BackfillService ──► SQLite
```

---

## Base de datos SQLite

### Estructura completa

```sql
-- ─────────────────────────────────────────
--  Estructura del fin de semana
-- ─────────────────────────────────────────

CREATE TABLE races (
    key         INTEGER PRIMARY KEY,   -- race_key de OpenF1/F1 timing
    name        TEXT NOT NULL,         -- "Bahrain Grand Prix"
    official_name TEXT,
    circuit     TEXT NOT NULL,         -- "Bahrain International Circuit"
    country     TEXT,
    year        INTEGER NOT NULL,
    round       INTEGER,
    date_start  DATE,
    date_end    DATE
);

CREATE TABLE sessions (
    key          INTEGER PRIMARY KEY,   -- session_key
    race_key  INTEGER REFERENCES races(key),
    name         TEXT NOT NULL,         -- "Qualifying"
    type         TEXT NOT NULL,         -- FP1|FP2|FP3|QUALIFYING|SPRINT|RACE
    status       TEXT,                  -- Started|Finished|Aborted
    date_start   DATETIME,
    date_end     DATETIME,
    recorded     BOOLEAN DEFAULT 0      -- 1 si tenemos datos propios en DB
);

-- ─────────────────────────────────────────
--  Datos de sesión (del bridge)
-- ─────────────────────────────────────────

CREATE TABLE session_drivers (
    session_key  INTEGER REFERENCES sessions(key),
    number       TEXT NOT NULL,         -- "1"
    code         TEXT NOT NULL,         -- "VER"
    first_name   TEXT,
    last_name    TEXT,
    team         TEXT,
    team_color   TEXT,                  -- "3671C6"
    PRIMARY KEY (session_key, number)
);

CREATE TABLE laps (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    session_key     INTEGER REFERENCES sessions(key),
    driver_number   TEXT NOT NULL,
    lap_number      INTEGER NOT NULL,
    lap_time_ms     INTEGER,            -- NULL si vuelta incompleta
    sector1_ms      INTEGER,
    sector2_ms      INTEGER,
    sector3_ms      INTEGER,
    is_personal_best BOOLEAN DEFAULT 0,
    is_overall_best  BOOLEAN DEFAULT 0,
    pit_out_lap     BOOLEAN DEFAULT 0,
    pit_in_lap      BOOLEAN DEFAULT 0,
    timestamp       DATETIME NOT NULL,
    UNIQUE(session_key, driver_number, lap_number)
);

CREATE TABLE stints (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    session_key   INTEGER REFERENCES sessions(key),
    driver_number TEXT NOT NULL,
    stint_number  INTEGER NOT NULL,
    compound      TEXT,                 -- SOFT|MEDIUM|HARD|INTERMEDIATE|WET
    is_new        BOOLEAN,
    lap_start     INTEGER,
    lap_end       INTEGER,              -- NULL si el stint sigue activo
    UNIQUE(session_key, driver_number, stint_number)
);

CREATE TABLE pit_stops (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    session_key   INTEGER REFERENCES sessions(key),
    driver_number TEXT NOT NULL,
    lap_number    INTEGER,
    timestamp     DATETIME NOT NULL
);

CREATE TABLE race_control_messages (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    session_key   INTEGER REFERENCES sessions(key),
    timestamp     DATETIME NOT NULL,
    category      TEXT,                 -- Flag|SafetyCar|Other
    message       TEXT NOT NULL,
    flag          TEXT,                 -- GREEN|YELLOW|RED|SC|VSC|CHEQUERED
    scope         TEXT,                 -- Track|Sector|Driver
    sector        INTEGER,
    driver_number TEXT,
    lap_number    INTEGER
);

CREATE TABLE weather_snapshots (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    session_key   INTEGER REFERENCES sessions(key),
    timestamp     DATETIME NOT NULL,
    air_temp      REAL,
    track_temp    REAL,
    humidity      REAL,
    pressure      REAL,
    wind_speed    REAL,
    wind_direction INTEGER,
    rainfall      BOOLEAN
);

-- Posición y gaps por timestamp (del TimingData del bridge)
-- Frecuencia: cada vez que cambia la posición (~1 vez por vuelta)
CREATE TABLE position_snapshots (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    session_key   INTEGER REFERENCES sessions(key),
    timestamp     DATETIME NOT NULL,
    driver_number TEXT NOT NULL,
    position      INTEGER,
    gap_to_leader TEXT,                 -- "+0.438" | "1 LAP" | ""
    interval      TEXT
);

-- Telemetría (CarData.z) — alta frecuencia, opcional
-- ~3.7 Hz × 20 pilotos × duración de sesión
-- Flag para habilitar/deshabilitar almacenamiento de telemetría
CREATE TABLE car_telemetry (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    session_key   INTEGER REFERENCES sessions(key),
    timestamp     DATETIME NOT NULL,
    driver_number TEXT NOT NULL,
    speed         INTEGER,
    rpm           INTEGER,
    gear          INTEGER,
    throttle      INTEGER,              -- 0-100
    brake         INTEGER,              -- 0-100
    drs           INTEGER               -- 0=closed, 8=eligible, 10=open
);

-- ─────────────────────────────────────────
--  Datos históricos (de Jolpica-F1 — backfill)
-- ─────────────────────────────────────────

CREATE TABLE race_results (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    session_key       INTEGER REFERENCES sessions(key),
    driver_code       TEXT NOT NULL,
    constructor_name  TEXT,
    grid_position     INTEGER,
    finish_position   INTEGER,          -- NULL si DNF
    points            REAL,
    status            TEXT,             -- "Finished" | "DNF" | "Engine" | etc.
    fastest_lap       BOOLEAN DEFAULT 0,
    fastest_lap_time  TEXT,
    laps_completed    INTEGER
);

CREATE TABLE driver_standings (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    year          INTEGER NOT NULL,
    after_round   INTEGER NOT NULL,     -- después de qué ronda
    driver_code   TEXT NOT NULL,
    position      INTEGER,
    points        REAL,
    wins          INTEGER,
    UNIQUE(year, after_round, driver_code)
);

CREATE TABLE constructor_standings (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    year             INTEGER NOT NULL,
    after_round      INTEGER NOT NULL,
    constructor_name TEXT NOT NULL,
    position         INTEGER,
    points           REAL,
    wins             INTEGER,
    UNIQUE(year, after_round, constructor_name)
);

-- ─────────────────────────────────────────
--  Índices
-- ─────────────────────────────────────────

CREATE INDEX idx_laps_session_driver   ON laps(session_key, driver_number);
CREATE INDEX idx_laps_session_lap      ON laps(session_key, lap_number);
CREATE INDEX idx_stints_session_driver ON stints(session_key, driver_number);
CREATE INDEX idx_position_session_ts   ON position_snapshots(session_key, timestamp);
CREATE INDEX idx_telemetry_session_ts  ON car_telemetry(session_key, timestamp);
CREATE INDEX idx_sessions_year         ON sessions(year, type);
CREATE INDEX idx_races_year         ON races(year);
```

### Tamaño estimado por sesión
| Tabla | Frecuencia | Tamaño estimado |
|-------|-----------|-----------------|
| laps | ~1/vuelta/piloto | ~50KB por sesión |
| stints | ~3/piloto | ~5KB |
| position_snapshots | ~1/vuelta/piloto | ~100KB |
| race_control_messages | ~20 por sesión | ~10KB |
| weather_snapshots | cada 30s | ~20KB |
| car_telemetry (opt.) | 3.7Hz/piloto | ~50MB por sesión |

**Sin telemetría**: ~200KB por sesión → una temporada completa (~30 sesiones) ≈ 6MB.
**Con telemetría** (default): ~50MB por sesión → temporada completa ≈ 1.5GB.

Telemetría activada por defecto. Se puede desactivar con `store_telemetry: false` si el espacio es una restricción.

---

## LiveTimingPersistenceService

Intercepta cada `TimingMessage` del bridge y lo persiste antes de enviarlo al StateManager.

```kotlin
class LiveTimingPersistenceService(
    private val lapRepo: LapRepository,
    private val stintRepo: StintRepository,
    private val pitRepo: PitStopRepository,
    private val raceControlRepo: RaceControlRepository,
    private val weatherRepo: WeatherRepository,
    private val positionRepo: PositionRepository,
    private val telemetryRepo: TelemetryRepository,
    private val config: AppConfig
) {
    suspend fun persist(sessionKey: Int, message: TimingMessage) {
        when (message) {
            is TimingMessage.LapUpdate       -> lapRepo.upsert(sessionKey, message)
            is TimingMessage.StintUpdate     -> stintRepo.upsert(sessionKey, message)
            is TimingMessage.PitStop         -> pitRepo.insert(sessionKey, message)
            is TimingMessage.RaceControlMsg  -> raceControlRepo.insert(sessionKey, message)
            is TimingMessage.WeatherUpdate   -> weatherRepo.insert(sessionKey, message)
            is TimingMessage.PositionUpdate  -> positionRepo.insert(sessionKey, message)
            is TimingMessage.CarDataUpdate   -> {
                if (config.storeTelemetry) telemetryRepo.insert(sessionKey, message)
            }
            else -> { /* SessionInfo, DriverList, etc. también persisten */ }
        }
    }
}
```

**Criterios de aceptación:**
- [ ] Cada mensaje del bridge se escribe a DB antes de enviarse al StateManager
- [ ] Upsert en laps: si llega una actualización del mismo lap, se actualiza (los sectores pueden llegar en mensajes separados)
- [ ] Si la DB falla (disco lleno, etc.), loguear el error pero NO crashear el servidor
- [ ] Telemetría desactivada por defecto (config `store_telemetry: false`)

---

## Arranque del servidor con datos históricos

```kotlin
// En LiveSessionStateManager.initialize()
suspend fun loadFromDatabase(sessionKey: Int): LiveSessionState {
    val drivers    = driverRepo.findBySession(sessionKey)
    val laps       = lapRepo.findBySession(sessionKey)
    val stints     = stintRepo.findBySession(sessionKey)
    val raceCtrl   = raceControlRepo.findBySession(sessionKey)
    val weather    = weatherRepo.findLatest(sessionKey)
    val positions  = positionRepo.findLatestPerDriver(sessionKey)

    return buildStateFromData(drivers, laps, stints, raceCtrl, weather, positions)
}
```

**Comportamiento en distintos escenarios:**
| Situación al arrancar | Comportamiento |
|----------------------|----------------|
| Sesión activa ahora mismo | Carga estado desde DB + conecta bridge para continuar |
| Sin sesión activa, datos de ayer | Carga última sesión desde DB, muestra estado final |
| Primera ejecución (DB vacía) | Estado vacío, espera primera sesión |
| Servidor apagado 1 semana | Muestra última sesión grabada, ofrece backfill |

---

## Backfill de sesiones perdidas

Cuando el servidor estaba apagado y hubo carreras, el usuario puede backfillear esos datos.

```
f1 data sync --year 2024 --round 15
```

**Fuentes para backfill:**
- **OpenF1 REST** (2023+): lap times, stints, pit stops, race control → tablas de sesión
- **Jolpica-F1** (histórico completo): race results, standings → tablas históricas

**Limitación conocida**: el backfill desde OpenF1 no tiene todos los campos del live timing (e.g., sector times son menos precisos). Se marca en la DB como `recorded = false` para distinguirlo de datos capturados en vivo.

```sql
-- sessions.recorded:
-- true  → capturado en vivo por el bridge (datos completos)
-- false → backfilleado desde API (datos parciales)
```

---

## Concurrencia

```
WsClientCoroutine  →  SharedFlow<TimingMessage>
                              │
              ┌───────────────┴───────────────┐
              ▼                               ▼
   PersistenceCoroutine              StateManagerCoroutine
   (escribe a SQLite)                (merge → StateFlow.update)
                                             │
                                    StateFlow<LiveSessionState>
                                             │
                              ┌──────────────┴──────────────┐
                              ▼                             ▼
                         SseCoroutine(s)            REST endpoints
```

- `SharedFlow` con `replay = 0` y buffer de 2000 — si la DB es lenta, los mensajes se acumulan en el buffer
- La DB usa un pool de 1 conexión para Exposed (SQLite no soporta bien la concurrencia de escritura)
- Las lecturas del REST/SSE usan una conexión read-only separada

---

## Configuración relevante

```yaml
# ~/.f1analytics/config.yml
db_path: ~/.f1analytics/f1.db
store_telemetry: true      # ~50MB/sesión — velocidad, RPM, DRS, throttle, brake a 3.7Hz
backfill_on_startup: false # true = busca sesiones perdidas al arrancar
server:
  port: 8080
  host: "0.0.0.0"
```
