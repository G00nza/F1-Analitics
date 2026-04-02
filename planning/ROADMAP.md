# F1 Analytics — Roadmap

## Visión
Herramienta web accesible desde cualquier dispositivo en la red local (PC, tablet, mobile). Conecta directamente al live timing oficial de F1 para mostrar datos en tiempo real durante cualquier sesión del fin de semana.

---

## Arquitectura en una línea

```
F1 Live Timing (SignalR) → bridge.py (Python/FastF1) → Ktor (Kotlin) → SSE → Browser
```

---

## Fuentes de datos

| Fuente | Para qué | Cómo |
|--------|----------|------|
| `livetiming.formula1.com` | Datos en vivo (sesión activa) | Python bridge con FastF1 SignalRClient |
| Jolpica-F1 API | Datos históricos (standings, resultados) | HTTP REST desde Ktor |

Sin rate limits en la fuente live. Sin costo. Sin auth.

---

## Iteraciones

### Iteración 0 — Live Session Viewer
> Abrir el browser, ver la sesión activa en tiempo real desde cualquier dispositivo.

- `f1 serve` arranca el bridge Python + el servidor Ktor
- Browser en `http://localhost:8080` o `http://192.168.x.x:8080` desde mobile
- Tabla de posiciones actualizada en vivo via SSE
- Sectores coloreados, neumáticos, clima, race control messages
- Responsive desde el día 1 (mobile/tablet)
- **Frontend**: Svelte + Vite (sin charts todavía)

### Iteración 1 — Rich Visualizations + Weekend Analysis
> Gráficos. Comparativa del fin de semana completo.

- Lap time chart, position chart (spaghetti), degradation chart, gap chart
- Análisis FP1→FP2→FP3→Q: progresión de tiempos, race pace desde FP
- **Frontend**: agrega Chart.js y navegación multi-vista sobre la base Svelte de IT-0

### Iteración 2 — Strategy & Qualifying
> Entender la estrategia antes y durante la carrera.

- Strategy preview pre-carrera basado en datos de FP
- Live strategy tracker con stints, compuestos, pit windows
- Undercut/overcut detector con alertas
- Safety Car impact analysis
- Qualifying analysis detallado

### Iteración 3 — Current Season
> Contexto de la temporada para el fin de semana.

- Standings actualizados (pilotos y constructores)
- Simulador de escenarios de campeonato
- Forma reciente por piloto (últimas 5 carreras)
- Preview del próximo GP con historial en el circuito

---

## Stack tecnológico

| Capa | Tecnología |
|------|-----------|
| **Live timing bridge** | Python 3.x + FastF1 + websockets |
| **Backend** | Kotlin 2.x + Ktor Server (CIO) |
| **HTTP Client** | Ktor Client |
| **Serialización** | kotlinx.serialization |
| **Real-time browser** | SSE (Ktor SSE plugin + browser EventSource) |
| **Cache local** | SQLite + Exposed ORM |
| **Frontend IT-0** | Svelte 4 + Vite (leaderboard, sin charts) |
| **Frontend IT-1+** | Svelte 4 + Vite + Chart.js (agrega charts y rutas) |
| **Build** | Gradle Kotlin DSL (multi-módulo) |
| **Tests** | Kotlin Test + MockK + Vitest |

---

## Índice de Epics

| ID | Nombre | Iteración |
|----|--------|-----------|
| [EP-01](epics/EP-01-data-infrastructure.md) | Bridge Python + clientes Kotlin + modelos | IT-0 |
| [EP-00](epics/EP-00-live-session-viewer.md) | Lógica live: StateManager, SseManager, polling | IT-0 |
| [EP-06](epics/EP-06-server-and-cli.md) | Ktor server: SSE, REST, static files, CLI | IT-0 |
| [EP-08](epics/EP-08-frontend-it0.md) | Frontend Svelte: leaderboard responsive + setup Vite | IT-0 |
| [EP-02](epics/EP-02-weekend-analysis.md) | Análisis de fin de semana (backend) | IT-1 |
| [EP-09](epics/EP-09-frontend-it1.md) | Frontend Svelte + todos los charts | IT-1 |
| [EP-03](epics/EP-03-strategy-race-day.md) | Estrategia y race day | IT-2 |
| [EP-04](epics/EP-04-qualifying-analysis.md) | Qualifying analysis | IT-2 |
| [EP-05](epics/EP-05-current-season.md) | Temporada en curso | IT-3 |
| [EP-07](epics/EP-07-quality.md) | Tests, logging, lint | Transversal |
