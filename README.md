# F1 Analytics — Planning Index

## Documentos principales

| Documento | Descripción |
|-----------|-------------|
| [ROADMAP.md](planning/ROADMAP.md) | Visión, fases, stack tecnológico e índice de epics |
| [technical/ARCHITECTURE.md](planning/technical/ARCHITECTURE.md) | Estructura de módulos, modelo de datos, convenciones |
| [technical/TECH-DECISIONS.md](planning/technical/TECH-DECISIONS.md) | ADRs — por qué elegimos cada tecnología |
| [use-cases/UC-CATALOG.md](planning/use-cases/UC-CATALOG.md) | Catálogo de 12 casos de uso principales |

## Epics por fase

### Fase 1 — MVP (fundación)
| Epic | Descripción | Esfuerzo |
|------|-------------|----------|
| [EP-01](planning/epics/EP-01-data-infrastructure.md) | Infraestructura de datos (modelos, Ergast API, SQLite) | ~7 días |
| [EP-02](planning/epics/EP-02-driver-analytics.md) | Análisis de pilotos (perfiles, H2H, rankings) | ~8 días |
| [EP-03](planning/epics/EP-03-race-analytics.md) | Análisis de carreras (resultados, temporadas, récords) | ~9 días |
| [EP-08](planning/epics/EP-08-cli-interface.md) | Interfaz CLI (Clikt, tablas, colores, REPL) | ~8 días |

### Fase 2 — Analytics Core
| Epic | Descripción | Esfuerzo |
|------|-------------|----------|
| [EP-04](planning/epics/EP-04-constructor-analytics.md) | Análisis de constructores | ~8 días |
| [EP-05](planning/epics/EP-05-strategy-analytics.md) | Análisis de estrategia (pit stops, undercut, SC) | ~8 días |
| [EP-06](planning/epics/EP-06-qualifying-analytics.md) | Análisis de qualifying y poles | ~6 días |
| [EP-07](planning/epics/EP-07-championship-tracker.md) | Tracker de campeonato y simulador | ~9 días |

### Fase 3 — Avanzado
| Epic | Descripción | Esfuerzo |
|------|-------------|----------|
| [EP-09](planning/epics/EP-09-predictive-analytics.md) | Análisis predictivo y rating ELO | ~11 días |
| [EP-10](planning/epics/EP-10-telemetry.md) | Telemetría FastF1 (Python bridge) | ~8 días |
| [EP-11](planning/epics/EP-11-historical-comparison.md) | Comparativa histórica entre eras | ~8 días |

### Fase 4 — Plataforma
| Epic | Descripción | Esfuerzo |
|------|-------------|----------|
| [EP-12](planning/epics/EP-12-rest-api.md) | REST API con Ktor + OpenAPI | ~7 días |
| [EP-13](planning/epics/EP-13-export-reports.md) | Exportación CSV/JSON/HTML | ~6.5 días |
| [EP-14](planning/epics/EP-14-quality.md) | Tests, logging, métricas, lint | ~7 días |

**Total estimado**: ~110 días de desarrollo

---

## Próximos pasos inmediatos (Fase 1)

1. Configurar Gradle multi-project (módulos: `core`, `data`, `analytics`, `cli`, `app`)
2. Agregar dependencias de Fase 1 al `build.gradle.kts`
3. Implementar EP-01: modelos de dominio primero, luego Ergast client
4. Implementar EP-08: estructura de comandos CLI (esqueleto)
5. Conectar EP-02 y EP-03 usando los datos de EP-01
