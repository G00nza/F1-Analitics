# Decisiones Técnicas (ADR)

## ADR-001: Python bridge sobre F1 live timing directo como única fuente live
**Decisión**: Un script Python (`bridge.py`) usa `fastf1.livetiming.SignalRClient` para conectar a `livetiming.formula1.com` y re-publica los datos via WebSocket local. El backend Kotlin se conecta a ese WebSocket.
**Razones**:
- `livetiming.formula1.com` usa el protocolo SignalR (Microsoft), para el que no existe cliente JVM bien mantenido.
- FastF1 ya tiene el cliente SignalR resuelto, probado y mantenido activamente.
- No hay rate limits (es el live timing oficial, público, sin auth).
- Latencia ~1-2s vs ~3-5s de OpenF1.
- Datos más completos: telemetría a 3.7Hz, team radio, todos los topics.
- Un bridge de ~50 líneas de Python evita implementar SignalR desde cero en Kotlin.
**Trade-off aceptado**: Requiere Python 3.x + pip en el entorno. Documentado en README.
**Alternativas rechazadas**:
- OpenF1 free REST: rate limit de 3 req/s insuficiente para polling en vivo.
- OpenF1 paid: costo recurrente innecesario dado que la fuente oficial es pública.
- Implementar SignalR en Kotlin: semanas de trabajo, frágil ante cambios del servidor.

---

## ADR-002: WebSocket (no SSE, no HTTP) entre bridge y Kotlin
**Decisión**: El bridge expone un servidor WebSocket en `localhost:9001`. Kotlin usa Ktor WebSocket Client para conectarse.
**Razones**:
- Los datos llegan como stream continuo de mensajes (push), WebSocket es el protocolo natural.
- Bidireccional: en el futuro el Kotlin podría enviar comandos al bridge (ej: "suscribir a topics adicionales").
- Ktor tiene soporte nativo de WebSocket client con `ktor-client-websockets`.
**Por qué no SSE del bridge**: SSE es HTTP, más complejo de implementar en Python como servidor que un WebSocket puro. `websockets` Python es la librería más simple.
**Por qué no HTTP polling al bridge**: Perdería el carácter push de los datos.

---

## ADR-003: SSE (no WebSocket) entre Ktor y el browser
**Decisión**: El servidor Ktor usa SSE para enviar datos al browser. El browser usa `EventSource`.
**Razones**:
- El flujo browser←servidor es unidireccional (solo el servidor envía datos).
- SSE es HTTP estándar, funciona sin configuración especial en routers/proxies de red local.
- El browser reconecta automáticamente si se cae la conexión (nativo de `EventSource`).
- Ktor tiene plugin nativo `ktor-server-sse`.
**Por qué no WebSocket hacia el browser**: Overkill para datos unidireccionales. SSE es más simple.

---

## ADR-004: Jolpica-F1 para datos históricos (reemplaza Ergast)
**Decisión**: Usar [Jolpica-F1](https://jolpi.ca/ergast/) como fuente de datos históricos.
**Razones**: Ergast fue dado de baja a fines de 2024. Jolpica-F1 es el reemplazo oficial drop-in con la misma API REST y el mismo formato JSON.
**Cobertura**: 1950–presente para resultados, standings, circuitos, pilotos.
**Cuándo se usa**: Análisis de fin de semana (historial en el circuito), standings de la temporada, datos pre-2023.

---

## ADR-005: StateFlow para el estado live
**Decisión**: `MutableStateFlow<LiveSessionState>` como fuente de verdad del estado de la sesión.
**Razones**: Thread-safe, siempre tiene el último valor (el SseManager puede conectarse en cualquier momento y recibe el estado actual), inmutable por diseño (`copy()`). Nativo de Kotlin Coroutines.

---

## ADR-006: Modelos delta-aware (merge incremental)
**Decisión**: El `LiveSessionStateManager` recibe mensajes delta del live timing y los mergea con el estado anterior, en lugar de recibir snapshots completos.
**Razones**: El F1 live timing envía deltas (solo los campos que cambiaron). Un merge correcto es esencial para no perder datos entre actualizaciones.
**Consecuencia**: Los modelos de dominio deben soportar campos `null` que indican "no cambió en este delta".

---

## ADR-007: Frontend IT-0 vanilla, IT-1+ Svelte
**Decisión**: Primera iteración en HTML/CSS/JS puro, sin framework ni build tools. A partir de IT-1, Svelte + Vite.
**Razones IT-0**: El leaderboard es una tabla simple. Sin estado complejo. Arrancar en días, no semanas. Cero configuración de toolchain.
**Razones IT-1+ Svelte**: Bundle pequeño (compila a JS vanilla), reactivo sin boilerplate, ideal para dashboards de datos que cambian frecuentemente. Más simple que React para un solo desarrollador.

---

## ADR-008: Chart.js para visualizaciones
**Decisión**: Chart.js para todos los gráficos del frontend.
**Razones**: API declarativa (config JSON → chart), soporte mobile/touch, suficiente para líneas, barras y scatter plots que necesitamos. Mucho más simple que D3.js para los casos de uso estándar.
**Cuándo reconsiderar D3**: Si necesitamos el mapa del circuito con posición de los coches en tiempo real (Position.z data).

---

## ADR-009: Gradle build produce un JAR único con frontend embebido
**Decisión**: `./gradlew build` ejecuta el build del frontend y embebe el resultado en los resources del JAR.
**Razones**: Un solo ejecutable simplifica distribución y arranque. No hay nginx, no hay servidor separado. Para uso en red local es suficiente.

---

## ADR-010: BridgeProcess como subprocess del JVM
**Decisión**: El proceso Kotlin arranca `bridge.py` como subprocess con `ProcessBuilder` y lo termina en el shutdown.
**Razones**: El usuario ejecuta solo `f1 serve` (o el JAR) y todo arranca. No hay que iniciar el bridge manualmente.
**Alternativa rechazada**: Que el usuario inicie el bridge por separado → peor UX, posibles puertos erróneos, sincronización manual.
**Requisito**: Python 3.x y las dependencias del bridge deben estar instaladas. El error si falta Python debe ser claro.
