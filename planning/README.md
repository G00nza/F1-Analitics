# F1 Analytics — Planning Index

## En una línea
```
F1 Live Timing (SignalR) → bridge.py → Ktor → SSE → Browser (PC / mobile / tablet)
```

## Documentos

| Documento | Descripción |
|-----------|-------------|
| [ROADMAP.md](ROADMAP.md) | Visión, iteraciones, stack |
| [technical/ARCHITECTURE.md](technical/ARCHITECTURE.md) | Módulos, flujo completo, protocolos, concurrencia |
| [technical/TECH-DECISIONS.md](technical/TECH-DECISIONS.md) | 10 ADRs con el razonamiento de cada decisión |
| [technical/SETUP.md](technical/SETUP.md) | Setup del entorno de desarrollo + hot reload |
| [use-cases/UC-CATALOG.md](use-cases/UC-CATALOG.md) | Casos de uso por iteración |

## Epics

### IT-0 — Live Session Viewer (~22 días)
| Epic | Qué construye |
|------|---------------|
| [EP-01](epics/EP-01-data-infrastructure.md) | `bridge.py` (Python/FastF1), `LiveTimingWsClient` (Kotlin), modelos de dominio, `JolpicaClient` |
| [EP-00](epics/EP-00-live-session-viewer.md) | `LiveSessionStateManager` (merge de deltas), `SseManager` (fan-out), detección de sesión |
| [EP-06](epics/EP-06-server-and-cli.md) | Ktor server, SSE endpoint, REST API, servir static files, `f1 serve` CLI |
| [EP-08](epics/EP-08-frontend-it0.md) | Frontend vanilla: leaderboard responsive, SSE client, header, race control |

### IT-1 — Visualizations + Weekend (~19 días)
| Epic | Qué construye |
|------|---------------|
| [EP-02](epics/EP-02-weekend-analysis.md) | Degradación FP, race pace, comparativa FP→Q (backend) |
| [EP-09](epics/EP-09-frontend-it1.md) | Svelte + lap time chart, position chart, degradation chart, gap chart |

### IT-2 — Strategy + Qualifying (~13 días)
| Epic | Qué construye |
|------|---------------|
| [EP-03](epics/EP-03-strategy-race-day.md) | Strategy preview, live tracker, undercut detector, SC impact |
| [EP-04](epics/EP-04-qualifying-analysis.md) | Qualifying result, gap analysis, correlación Q→R |

### IT-3 — Current Season (~7 días)
| Epic | Qué construye |
|------|---------------|
| [EP-05](epics/EP-05-current-season.md) | Standings, simulador campeonato, forma reciente, preview GP |

### Transversal
| Epic | Qué construye |
|------|---------------|
| [EP-07](epics/EP-07-quality.md) | Tests unitarios, integración, snapshot, logging, lint |

**Total estimado**: ~66 días

---

## Orden de implementación — IT-0

```
1. Setup multi-módulo Gradle (core / data / analytics / server / app)
   └── Actualizar a Kotlin 2.x, agregar dependencias IT-0

2. bridge/bridge.py  ← validar que recibe datos del F1 live timing
   └── python3 bridge.py → mensajes JSON en localhost:9001

3. EP-01: LiveTimingWsClient  ← Kotlin conecta al bridge
   └── + modelos de dominio (SessionInfo, DriverEntry, TimingData…)

4. EP-00: LiveSessionStateManager  ← merge de deltas → StateFlow
   └── + SseManager (fan-out del StateFlow a browsers)

5. EP-06: Ktor server  ← SSE /api/events/live + REST base + static files
   └── + BridgeProcess (arranca bridge.py como subprocess)

6. EP-08: Frontend vanilla  ← leaderboard + SSE client + responsive
```

## Prereqs del entorno

```bash
# Python
python3 --version          # 3.10+
pip install fastf1 websockets

# Kotlin/JVM
java --version             # 11+
./gradlew --version        # 8.x
```
