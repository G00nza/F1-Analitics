# EP-07: Calidad y Tests

**Iteración**: Transversal (desde el inicio)
**Prioridad**: Alta para las bases, Media para el resto
**Estado**: Pendiente

## Features

### F-07.1: Tests unitarios — lógica de análisis
Lo más importante de testear: los cálculos.

**Qué testear:**
- `LiveSessionState.merge()`: que el merge incremental no duplica datos
- Cálculo de degradación de neumático: con datos sintéticos conocidos
- Detección de undercut: escenario simulado donde el undercut se detecta correctamente
- Simulador de campeonato: resultado de 2023 debe coincidir con standings reales
- Conversión de tiempos: ms ↔ Duration ↔ display string ("1:31.344")

**Herramientas:**
- kotlin.test + JUnit5 platform
- MockK para mockear el cliente OpenF1

**Criterios de aceptación:**
- [ ] Tests corren sin red ni base de datos
- [ ] Coverage >75% en módulos `analytics` y `domain`

---

### F-07.2: Tests de integración con OpenF1
Verificar que el cliente HTTP mapea correctamente los responses.

**Estrategia:**
- Guardar responses reales de OpenF1 como fixtures JSON en `src/test/resources/`
- Tests usan un MockEngine de Ktor (no HTTP real)
- Un test de "smoke test" que sí llama a la API real (tag `@LiveIntegration`, no corre en CI)

**Criterios de aceptación:**
- [ ] Fixtures para cada endpoint relevante
- [ ] Tests con datos reales de una sesión conocida (ej: Q 2024 Bahrain)
- [ ] `./gradlew test` no hace llamadas HTTP reales

---

### F-07.3: Tests del rendering (snapshot tests)
El output del dashboard debe ser determinístico.

**Estrategia:**
- Para un `LiveSessionState` fijo, el output del renderer debe ser siempre igual
- Comparar contra snapshots guardados

**Criterios de aceptación:**
- [ ] Al menos 1 snapshot test del leaderboard completo
- [ ] `./gradlew updateSnapshots` para regenerar cuando el design cambia

---

### F-07.4: Logging
```kotlin
// En cada componente clave:
logger.debug { "Fetching laps since $lastPoll for session $sessionKey" }
logger.info  { "Session $sessionKey loaded: ${drivers.size} drivers" }
logger.warn  { "OpenF1 request took ${elapsed}ms (threshold: 5000ms)" }
logger.error { "Failed to connect to OpenF1 after 3 retries: $cause" }
```

**Criterios de aceptación:**
- [ ] `LOG_LEVEL=DEBUG f1 live` muestra detalles de cada request
- [ ] Logs no aparecen en el dashboard normal (van a stderr o archivo)

---

### F-07.5: Linting
- ktlint + detekt configurados en Gradle
- `./gradlew lint` pasa antes de cualquier commit

---

## Estimación de esfuerzo
- F-07.1 Tests unitarios: 2 días
- F-07.2 Tests integración: 1 día
- F-07.3 Snapshot tests: 1 día
- F-07.4 Logging: 0.5 días
- F-07.5 Lint: 0.5 días
- **Total**: ~5 días (+ ongoing)
