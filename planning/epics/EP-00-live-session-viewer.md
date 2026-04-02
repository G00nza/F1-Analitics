# EP-00: Live Session Viewer — Lógica de Backend

**Iteración**: 0
**Prioridad**: Crítica
**Dependencias**: EP-01 (bridge + WsClient + repositorios)
**Estado**: Pendiente

## Descripción
Lógica de backend del viewer en vivo: consumir los mensajes del bridge, mantener el estado de la sesión actualizado y distribuirlo a los browsers via SSE. Todo el estado viene de la DB — el bridge la alimenta en vivo, los repositorios la consultan al arrancar.

---

## Flujo de datos

```
bridge.py
    │ WebSocket push
    ▼
LiveTimingWsClient  →  SharedFlow<TimingMessage>
                                │
              ┌─────────────────┴─────────────────┐
              ▼                                   ▼
LiveTimingPersistenceService            LiveSessionStateManager
(escribe a DB — EP-01)                  (merge incremental → StateFlow)
                                                  │
                                        StateFlow<LiveSessionState>
                                                  │
                                             SseManager
                                          (fan-out a browsers)
```

**Regla:** el `LiveSessionStateManager` nunca llama a APIs externas. Solo recibe `TimingMessage` ya persistidos y consulta los repositorios de la DB.

---

## Features

### F-00.1: Event processing loop
Consumir el `SharedFlow<TimingMessage>` del `LiveTimingWsClient` y alimentar el `LiveSessionStateManager`.

```kotlin
class LiveSessionService(
    private val wsClient: LiveTimingWsClient,
    private val persistenceService: LiveTimingPersistenceService,
    private val stateManager: LiveSessionStateManager,
    private val scope: CoroutineScope
) {
    fun start() {
        scope.launch {
            wsClient.messages.collect { message ->
                // 1. Persistir primero
                persistenceService.persist(stateManager.currentSessionKey, message)
                // 2. Actualizar estado en memoria
                stateManager.merge(message)
            }
        }
    }
}
```

No hay polling — el bridge hace push de cada evento en cuanto lo recibe del live timing de F1. Cuando no hay sesión activa, el bridge sigue conectado pero solo llegan `Heartbeat`. El `stateManager` los ignora.

**Criterios de aceptación:**
- [ ] Si el bridge se desconecta, el `LiveTimingWsClient` reintenta (EP-01). El loop de collect simplemente espera.
- [ ] Mensajes `Heartbeat` no disparan actualización del `StateFlow`
- [ ] El loop se detiene limpiamente con la cancelación del `CoroutineScope`

---

### F-00.2: LiveSessionStateManager — merge incremental
Mantiene el `StateFlow<LiveSessionState>` actualizado aplicando los deltas del live timing.

```kotlin
class LiveSessionStateManager(
    private val sessionRepo: SessionRepository,
    private val lapRepo: LapRepository,
    private val stintRepo: StintRepository,
    private val raceControlRepo: RaceControlRepository,
    private val weatherRepo: WeatherRepository
) {
    private val _stateFlow = MutableStateFlow<LiveSessionState?>(null)
    val stateFlow: StateFlow<LiveSessionState?> = _stateFlow.asStateFlow()

    var currentSessionKey: Int = -1
        private set

    /** Carga el estado desde la DB al arrancar o al cambiar de sesión */
    suspend fun loadFromDb(sessionKey: Int) {
        currentSessionKey = sessionKey
        val drivers    = sessionRepo.findDrivers(sessionKey)
        val laps       = lapRepo.findBySession(sessionKey)
        val stints     = stintRepo.findBySession(sessionKey)
        val raceCtrl   = raceControlRepo.findBySession(sessionKey)
        val weather    = weatherRepo.findLatest(sessionKey)
        val positions  = lapRepo.findLatestPositions(sessionKey)

        _stateFlow.value = buildState(sessionKey, drivers, laps, stints,
                                      raceCtrl, weather, positions)
    }

    /** Aplica un delta recibido del bridge (ya persistido en DB) */
    fun merge(message: TimingMessage) {
        _stateFlow.update { current ->
            when (message) {
                is TimingMessage.DriverListMsg    -> current?.withDrivers(message.drivers)
                is TimingMessage.TimingDataMsg    -> current?.withTimingDeltas(message.deltas)
                is TimingMessage.TimingAppDataMsg -> current?.withStintDeltas(message.deltas)
                is TimingMessage.RaceControlMsg   -> current?.withRaceControl(message)
                is TimingMessage.WeatherMsg       -> current?.copy(weather = message.weather)
                is TimingMessage.TrackStatusMsg   -> current?.copy(trackStatus = message.status)
                is TimingMessage.SessionStatusMsg -> current?.copy(sessionStatus = message.status)
                is TimingMessage.ExtrapolatedClockMsg -> current?.copy(timeRemaining = message.remaining)
                is TimingMessage.LapCountMsg      -> current?.copy(lapCount = LapCountData(message.current, message.total))
                is TimingMessage.HeartbeatMsg     -> current  // sin cambios
                else -> current
            }
        }
    }
}
```

**Criterios de aceptación:**
- [ ] `loadFromDb()` reconstruye el estado completo desde la DB — el resultado es idéntico al que habría si hubiéramos estado escuchando en vivo desde el principio
- [ ] `merge()` aplica deltas parciales — los campos no presentes en el delta quedan sin cambio
- [ ] `bestLap` y `lastLap` se recalculan desde la lista de vueltas al aplicar un `TimingDataMsg`
- [ ] Thread-safe: `StateFlow.update` es atómico

---

### F-00.3: Selección de sesión al arrancar
Al iniciar el servidor, determinar qué sesión cargar.

```kotlin
class SessionResolver(
    private val sessionRepo: SessionRepository
) {
    suspend fun resolve(): Session? {
        // 1. ¿Hay una sesión activa ahora mismo en la DB?
        sessionRepo.findActive()?.let { return it }

        // 2. ¿Hay una sesión reciente (últimas 4h) en la DB?
        sessionRepo.findMostRecent()?.let { return it }

        // 3. Sin sesión disponible → null (UI muestra estado idle)
        return null
    }

    suspend fun findUpcoming(): Session? {
        // Solo consulta la DB local — sin llamadas externas
        return sessionRepo.findNextUpcoming()
    }
}
```

**Regla:** el servidor solo lee la DB. Si la DB está vacía, el usuario debe correr `f1 data init` primero para poblar el calendario.

**Criterios de aceptación:**
- [ ] Al arrancar con sesión activa → carga desde DB y empieza a recibir eventos del bridge
- [ ] Al arrancar sin sesión activa → carga última sesión grabada desde DB para mostrar en el browser
- [ ] `GET /api/sessions/latest` usa este mismo criterio
- [ ] Si la DB está vacía → estado idle, sin errores, sin llamadas a APIs externas

---

### F-00.4: Detección de inicio de sesión
Cuando el servidor está idle, detectar automáticamente cuándo empieza la próxima sesión.

```kotlin
class SessionWatcher(
    private val sessionRepo: SessionRepository,
    private val stateManager: LiveSessionStateManager,
    private val sseManager: SseManager,
    private val scope: CoroutineScope
) {
    fun start() {
        scope.launch {
            while (isActive) {
                val upcoming = sessionRepo.findNextUpcoming() ?: run {
                    delay(5.minutes); return@run null
                } ?: continue

                val startsIn = upcoming.dateStart - Clock.System.now()

                when {
                    startsIn > 10.minutes -> delay(startsIn - 10.minutes)

                    startsIn > Duration.ZERO -> {
                        // Avisar al browser que la sesión está por empezar
                        sseManager.broadcast(SessionStatusEvent.StartingSoon(upcoming, startsIn))
                        delay(startsIn)
                    }

                    else -> {
                        // La sesión debería estar activa — cargar desde DB
                        // (el bridge ya estará recibiendo eventos si el live timing está activo)
                        stateManager.loadFromDb(upcoming.key)
                        delay(1.minutes)
                    }
                }
            }
        }
    }
}
```

**Criterios de aceptación:**
- [ ] El browser recibe evento SSE `session_starting_soon` con countdown
- [ ] Al empezar la sesión, el estado se actualiza automáticamente sin acción del usuario
- [ ] El calendario de sesiones viene de la DB — si está vacío, `SessionWatcher` queda en espera sin errores

---

### F-00.5: Replay de sesión desde DB
Ver cualquier sesión grabada como si fuera en vivo, usando los datos almacenados.

```kotlin
// Endpoint: GET /api/events/replay/{sessionKey}?speed=10
sse("/api/events/replay/{sessionKey}") {
    val sessionKey = call.parameters["sessionKey"]!!.toInt()
    val speed = call.request.queryParameters["speed"]?.toDouble() ?: 1.0

    // Cargar todos los eventos cronológicos desde la DB
    val events = replayRepo.findAllEventsBySession(sessionKey)  // ordenados por timestamp

    var lastTimestamp: Instant? = null
    for (event in events) {
        lastTimestamp?.let { prev ->
            val realDelay = (event.timestamp - prev) / speed
            delay(realDelay.coerceIn(Duration.ZERO, 5.seconds))
        }
        send(ServerSentEvent(data = event.toSseJson(), event = event.topic))
        lastTimestamp = event.timestamp
    }
}
```

**La DB ya tiene todo** — no hay que guardar nada extra para el replay. Se reconstruye leyendo `laps`, `position_snapshots`, `race_control_messages`, `weather_snapshots` en orden cronológico.

**Criterios de aceptación:**
- [ ] Mismo formato SSE que el live — el frontend no distingue entre live y replay
- [ ] `speed=1` → tiempo real, `speed=10` → 10x, `speed=60` → ~1 min por hora de sesión
- [ ] Solo disponible para sesiones con `recorded = true` en la DB

---

### F-00.6: SseManager — fan-out a múltiples clientes

```kotlin
class SseManager(private val stateManager: LiveSessionStateManager) {

    suspend fun handleLiveClient(session: SseServerSession) {
        // Estado inicial completo desde la DB
        stateManager.stateFlow.value?.let {
            session.send(ServerSentEvent(
                data = json.encodeToString(it.toDto()),
                event = "session_state"
            ))
        }

        // Updates incrementales via StateFlow
        stateManager.stateFlow
            .filterNotNull()
            .distinctUntilChanged()
            .collect { state ->
                session.send(ServerSentEvent(
                    data = json.encodeToString(state.toDto()),
                    event = "session_state"
                ))
            }
    }

    suspend fun broadcast(event: SessionStatusEvent) {
        // Para eventos de sistema (session starting soon, session ended, etc.)
        // implementado con un SharedFlow de eventos de sistema separado
    }
}
```

**Criterios de aceptación:**
- [ ] El primer mensaje al conectar es el estado completo actual (desde `stateFlow.value`)
- [ ] `distinctUntilChanged()` evita re-enviar el mismo estado si no cambió nada
- [ ] Si un cliente se desconecta, el `collect` se cancela solo (structured concurrency)
- [ ] Heartbeat cada 30s si no hubo otros eventos (previene timeout de proxies)

---

## Estimación de esfuerzo
- F-00.1 Event processing loop: 0.5 días
- F-00.2 StateManager + merge: 2 días
- F-00.3 Selección de sesión: 1 día
- F-00.4 Session watcher: 1 día
- F-00.5 Replay desde DB: 1.5 días
- F-00.6 SseManager: 1 día
- **Total**: ~7 días
