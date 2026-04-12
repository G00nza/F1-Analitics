# EP-03: Estrategia y Race Day

**Iteración**: 2
**Prioridad**: Alta
**Dependencias**: EP-01, EP-02
**Estado**: Pendiente

## Descripción
Análisis de estrategia antes y durante la carrera: predicción de ventanas de pit stop, análisis de stints en tiempo real, undercuts, overcuts y impacto del safety car.

---

## Features

### F-03.1: Pre-race strategy preview
Antes de la carrera, predecir las estrategias más probables.

**Inputs:**
- Datos de degradación de FP2 y FP3
- Compuesto de ruedas sin usar 
- Número de vueltas del circuito
- Historial de safety cars en ese circuito (épocas previas, si hay datos)

**Output:**
```
Strategy Preview — Bahrain GP 2024 (57 laps)
Based on FP2 long runs + Qualifying compounds

Driver | Q compound | Expected strategy          | Alt strategy
VER    | SOFT       | SOFT(15) → HARD(42)        | SOFT(15)→MED(20)→HARD(22)
LEC    | SOFT       | SOFT(18) → HARD(39)        | SOFT(12)→HARD(45)
NOR    | MEDIUM     | MED(25)  → HARD(32)        | 1-stop any if SC
...

Optimal pit window: laps 14-20 for SOFT starters
Warning: High track temp expected → increased degradation (+15% estimate)
```

**Criterios de aceptación:**
- [x] Calculado a partir de la tasa de degradación de EP-02.3
- [x] Considerar los compuestos disponibles
- [x] Mostrar ventana óptima de pit stop como rango de vueltas

---

### F-03.2: Live strategy tracker durante la carrera
Durante la carrera, mostrar la estrategia de cada piloto en tiempo real.

**Vista:**
```
Strategy Tracker — Bahrain GP 2024 | LAP 23/57

Driver | Pos | Compound | Stint laps | FP window  | Real window | Status
VER    |  1  | HARD ●   |    +8      | Lap 35-40  | Lap 33-38   | On track
HAM    |  2  | MEDIUM ● |   +13      | Lap 28-32  | Lap 27-31   | On track
LEC    |  3  | SOFT ●   |   +23      | Lap 18-22  | OVERDUE ⚠️  | On track  (pitting next lap?)
SAI    |  P  | —        |    —       | —          | —           | IN PIT    (stop 1, lap 22)
...

● SOFT=red  ● MEDIUM=yellow  ● HARD=white  ● INTER=green  ● WET=blue
```
- **FP window**: ventana teórica calculada a partir de la degradación observada en FP2/FP3
- **Real window**: ventana ajustada en tiempo real usando la degradación actual del stint (delta de tiempos de vuelta reales vs baseline)

**Criterios de aceptación:**
- [x] Integrado como pestaña adicional del live viewer (EP-00)
- [x] Actualización en tiempo real con los datos de stints de OpenF1
- [x] Marcar "OVERDUE" cuando el piloto lleva más vueltas que la ventana real
- [x] Mostrar ambas columnas (FP window y Real window) con diferencia visual cuando divergen significativamente (>2 vueltas)

---

### F-03.3: Undercut/Overcut detector
Detectar y alertar cuando hay un undercut o overcut en curso, solo sobre los conductores que el usuario quiere monitorear.

**Detección (event-driven + poll selectivo):**
- **Undercut**: se dispara cuando un piloto monitorizado (o su rival directo) entra a boxes → revisar rivales en pista dentro de un gap configurable
- **Overcut en curso**: poll periódico solo sobre pares adyacentes en posición donde ambos están monitorizados, el gap es < umbral y uno tiene neumático >15 vueltas

**Control de conductores a monitorizar:**
- **Favoritos globales**: lista configurada en settings, persistente entre sesiones
- **Override por carrera**: selección editable en la UI antes de la carrera, precargada con los favoritos globales
- **Control durante la carrera**: el usuario puede agregar o quitar conductores del monitoreo en cualquier momento sin interrumpir la sesión activa

**Alert:**
```
🟠 UNDERCUT ALERT [Lap 28]
NOR (P4) pitted → HARD (new)
Gap to ALO (P3, MEDIUM+19 laps): 2.1s
If NOR laps 0.5s faster for 3 laps, undercut succeeds
Predicted outcome: NOR P3 after pit stop window closes
```

**Criterios de aceptación:**
- [x] Configuración global de favoritos persistente en settings
- [x] Selector de conductores editable antes y durante la carrera
- [x] El detector solo actúa sobre pares donde al menos un conductor está en la lista activa
- [x] Alerta visual en el live viewer cuando se detecta un undercut o overcut en curso
- [x] Post-carrera: listar todos los undercuts/overcuts detectados (solo de conductores monitorizados) con resultado

---

### F-03.4: Impacto del Safety Car en la estrategia
El Safety Car cambia completamente la estrategia — detectarlo y mostrar el impacto.

**Durante SC:**
```
🚗 SAFETY CAR DEPLOYED [Lap 31] — Incident: HAM, Turn 14

Strategy impact:
Drivers NOT yet pitted (free stop opportunity):
  VER (P1) HARD+16 — MUST PIT NOW (free stop)
  LEC (P2) HARD+16 — Consider pit (close call)

Drivers already pitted:
  NOR (P3) HARD+8  — No action needed

If VER pits now: expected P1 after stop ✓
If VER stays out: risk losing position to pitting drivers ⚠️
```

**Criterios de aceptación:**
- [x] Alerta inmediata cuando OpenF1 reporta Safety Car en Race Control
- [x] Cálculo automático de quién se beneficia de parar bajo SC
- [x] Post-carrera: análisis de qué equipos capitalizaron el SC correctamente

---

### F-03.5: Análisis post-carrera de estrategia
Después de la carrera, review completo de las decisiones estratégicas.

**Contenido:**
- Estrategia final de cada piloto (compuestos, vueltas de pit stop)
- Ganador/perdedor estratégico del día
- Análisis de la decisión más impactante (undercut, SC, 1-stop vs 2-stop)
- Resumen: quién eligió mejor estrategia dado el contexto

**Output:**
```
Post-race Strategy Review — Bahrain GP 2024

Winner: VER — SOFT(14)→HARD(43) — 1-stop
  ✓ Perfect undercut on LEC at lap 14 (+0.3s advantage in pit lane)

Biggest story: NOR 2-stop strategy backfired
  NOR: SOFT(12)→MED(20)→HARD(25) — lost 8s in 2 pit stops
  vs competitors on 1-stop — finished P6 vs expected P4

Safety Car (Lap 31) beneficiary: ALO
  ALO pitted for free stop → gained 3 positions
```

**Criterios de aceptación:**
- [ ] Disponible inmediatamente después de que termina la sesión
- [ ] Accessible via `f1 strategy review` sin argumentos para la última carrera

---

## Estimación de esfuerzo
- F-03.1 Pre-race preview: 2 días
- F-03.2 Live tracker: 2 días
- F-03.3 Undercut detector: 2 días
- F-03.4 Safety Car impact: 1 día
- F-03.5 Post-race review: 2 días
- **Total**: ~9 días
