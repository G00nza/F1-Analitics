# EP-06: Servidor Web + CLI mínima

**Iteración**: 0 — bloqueante para el frontend
**Prioridad**: Crítica
**Dependencias**: EP-01 (bridge + LiveTimingWsClient + repositorios)
**Estado**: Pendiente

## Descripción
Levantar el servidor Ktor que:
1. Sirve el frontend estático
2. Expone SSE para updates en tiempo real
3. Expone REST API para datos bajo demanda
4. Es accesible desde cualquier dispositivo en la red local

La CLI queda reducida a `f1 serve [--port N]` más algunas opciones de config.

---

## Features

### F-06.1: Servidor Ktor base
Configuración del servidor con todos los plugins necesarios.

**Plugins a configurar:**
```kotlin
fun Application.configureServer() {
    install(ContentNegotiation) {
        json(Json { prettyPrint = false; ignoreUnknownKeys = true })
    }
    install(CORS) {
        anyHost()                  // desarrollo: cualquier origen
        allowHeader(HttpHeaders.ContentType)
    }
    install(Compression) {
        gzip()
        deflate()
    }
    install(DefaultHeaders)
    install(CallLogging) { level = Level.INFO }
    install(StaticContent)         // sirve /static/ automáticamente
    install(SSE)                   // Ktor SSE plugin
}
```

**Criterios de aceptación:**
- [x] `GET /` devuelve el `index.html` del frontend
- [x] `GET /health` devuelve `{"status": "ok", "session": "ACTIVE|IDLE"}`
- [x] Servidor arranca en `0.0.0.0:8080` por defecto
- [x] Logs muestran cada request en INFO

---

### F-06.2: Endpoints SSE (Server-Sent Events)
El canal de datos en tiempo real del servidor al browser.

**Endpoint principal:**
```
GET /api/events/live
Accept: text/event-stream
```

**Implementación con Ktor SSE:**
```kotlin
routing {
    sse("/api/events/live") {
        // 1. Mandar estado inicial completo al conectar
        send(ServerSentEvent(
            data = Json.encodeToString(stateManager.currentState),
            event = "session_state"
        ))

        // 2. Suscribirse al StateFlow y hacer fan-out
        stateManager.stateFlow
            .drop(1)                              // ya mandamos el primero arriba
            .collect { state ->
                send(ServerSentEvent(
                    data = Json.encodeToString(state.toDto()),
                    event = "session_state"
                ))
            }
    }
}
```

**Tipos de eventos SSE:**

| Event name | Cuándo se emite | Payload |
|------------|-----------------|---------|
| `session_state` | Cada update del StateFlow (si hay cambios) | `LiveSessionStateDto` completo |
| `race_control` | Nuevo mensaje de race control | `RaceControlDto` |
| `session_status` | Cambio de estado de sesión (empieza, termina, próxima) | `SessionStatusEventDto` |
| `heartbeat` | Cada 30s si no hubo otros eventos | `HeartbeatDto` |

**DTOs de eventos SSE:**
```kotlin
@Serializable
data class SessionStatusEventDto(
    val status: String,              // "ACTIVE" | "FINISHED" | "ABORTED" | "IDLE"
    val sessionType: String?,        // "FP1" | "FP2" | "FP3" | "QUALIFYING" | "SPRINT" | "RACE" | null si IDLE
    val sessionName: String?,        // "Bahrain Grand Prix — Qualifying"
    val timeRemaining: String?,      // "1:23:45" | null
    val startingSoon: Boolean = false,
    val minutesUntilStart: Int? = null
)

@Serializable
data class HeartbeatDto(
    val timestamp: String            // ISO-8601
)
```

**Criterios de aceptación:**
- [x] Múltiples browsers conectados simultáneamente (fan-out correcto)
- [x] Si el servidor no tiene sesión activa, manda `session_status: IDLE` y sigue esperando
- [x] El heartbeat previene que el browser cierre la conexión por timeout
- [x] Browser reconecta automáticamente si se cae la conexión (comportamiento nativo de EventSource)

---

### F-06.3: REST API endpoints (IT-0)
Los endpoints mínimos para que el frontend funcione en IT-0.

```
GET /api/sessions/latest
    → { key, name, type, status, meetingName, circuit, dateStart }

GET /api/sessions/{key}/state
    → LiveSessionStateDto (snapshot del estado actual)

GET /api/meetings?year=2024
    → [{ key, name, circuit, country, dateStart, sessions: [...] }]

GET /api/meetings/current
    → meeting con fechas más próximas al día de hoy
```

**DTOs (lo que el frontend recibe — no son las entidades de dominio):**
```kotlin
@Serializable
data class LiveSessionStateDto(
    val sessionKey: Int,
    val sessionName: String,
    val sessionType: String,
    val sessionStatus: String,
    val leaderboard: List<DriverLiveDataDto>,
    val raceControl: List<RaceControlDto>,
    val weather: WeatherDto?,
    val lastUpdated: String          // ISO-8601
)

@Serializable
data class DriverLiveDataDto(
    val position: Int,
    val driverNumber: Int,
    val driverCode: String,
    val teamName: String,
    val teamColor: String,           // hex: "#E8002D"
    val bestLapTime: String?,        // "1:29.179"
    val lastLapTime: String?,
    val gapToLeader: String?,        // "+0.438" o "leader"
    val interval: String?,
    val sector1: String?,
    val sector2: String?,
    val sector3: String?,
    val tyreCompound: String,        // "SOFT" | "MEDIUM" | "HARD" | "INTER" | "WET"
    val tyreIsNew: Boolean,
    val lapsOnTyre: Int,
    val status: String               // "ON_TRACK" | "IN_PIT" | "OUT_LAP"
)
```

```kotlin
@Serializable
data class RaceControlDto(
    val timestamp: String,           // ISO-8601
    val message: String,
    val flag: String?,               // "GREEN" | "YELLOW" | "RED" | "SC" | "VSC" | "CHEQUERED" | null
    val category: String?,           // "Flag" | "SafetyCar" | "Other"
    val scope: String?,              // "Track" | "Sector" | "Driver"
    val sector: Int?,
    val driverNumber: String?,
    val lapNumber: Int?
)

@Serializable
data class WeatherDto(
    val airTemp: Double,             // °C
    val trackTemp: Double,           // °C
    val humidity: Double,            // %
    val pressure: Double,            // mbar
    val windSpeed: Double,           // m/s
    val windDirection: Int,          // grados 0-359
    val rainfall: Boolean
)
```

**Criterio de tiempos:** todos los tiempos de vuelta, sectores y gaps se representan como `String?` tal como vienen del bridge (ej. `"1:29.179"`, `"29.432"`, `"+0.438"`, `"1 LAP"`). Los datos climáticos son numéricos para que el frontend pueda formatear/convertir unidades.

**Criterios de aceptación:**
- [x] Todos los tiempos formateados como strings legibles (no ms crudos)
- [x] `teamColor` en hex para usar directamente en CSS
- [x] `null` cuando el dato no está disponible (no strings vacíos)

---

### F-06.4: Servir el frontend estático
Ktor sirve los archivos del frontend desde los resources.

Ktor sirve el build de Svelte/Vite desde los resources:
```kotlin
staticResources("/", "static") {
    default("index.html")           // SPA fallback
}
```

El Gradle task `buildFrontend` (definido en EP-08) copia `frontend/dist/` a `server/src/main/resources/static/` antes del `processResources`. En desarrollo se usa Vite dev server en `:5173` con proxy al backend en `:8080`.

**Criterios de aceptación:**
- [x] `GET /` sirve `index.html`
- [x] `GET /app.js`, `GET /styles.css` funcionan
- [x] URLs desconocidas devuelven `index.html` (SPA routing)
- [ ] Assets con cache headers apropiados (inmutables para hashed filenames en IT-1+)

---

### F-06.5: CLI mínima
La CLI en IT-0 es solo el launcher del servidor.

**Comandos:**
```bash
f1 serve                        # arranca en 0.0.0.0:8080
f1 serve --port 9090            # puerto custom
f1 serve --no-open              # no abrir browser automáticamente

f1 data init                    # carga calendario + standings del año actual desde Jolpica
f1 data init --year 2024        # año específico
f1 data sync --round 15         # backfill de una carrera puntual (resultados, standings)
```

**Flujo de primera ejecución:**
```bash
f1 data init        # ← una vez, para poblar el calendario en la DB
f1 serve            # ← a partir de acá, el servidor nunca llama a Jolpica
```

**Comportamiento al arrancar:**
1. Inicia Ktor en el puerto configurado
2. Imprime la URL de acceso local y de red:
   ```
   F1 Analytics server running
   Local:   http://localhost:8080
   Network: http://192.168.1.42:8080
   ```
3. Abre el browser automáticamente (a menos que `--no-open`)
4. Inicia el bridge Python como subprocess y conecta el LiveTimingWsClient

**Criterios de aceptación:**
- [x] Detecta la IP local automáticamente para mostrarla al usuario
- [x] `Ctrl+C` hace graceful shutdown (termina el polling, cierra conexiones SSE)
- [ ] Si el puerto está ocupado, muestra error claro con sugerencia de `--port`

---

## Dependencias técnicas

```kotlin
// server/build.gradle.kts
dependencies {
    implementation("io.ktor:ktor-server-core:2.3.12")
    implementation("io.ktor:ktor-server-cio:2.3.12")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-server-cors:2.3.12")
    implementation("io.ktor:ktor-server-compression:2.3.12")
    implementation("io.ktor:ktor-server-default-headers:2.3.12")
    implementation("io.ktor:ktor-server-call-logging:2.3.12")
    implementation("io.ktor:ktor-server-sse:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    implementation("com.github.ajalt.clikt:clikt:4.3.0")
}
```

## Estimación de esfuerzo
- F-06.1 Servidor base: 1 día
- F-06.2 SSE: 2 días
- F-06.3 REST endpoints IT-0: 1 día
- F-06.4 Static files: 0.5 días
- F-06.5 CLI launcher: 0.5 días
- **Total**: ~5 días
