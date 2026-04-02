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
- Compuesto de qualifying usado (obliga a empezar con ese compuesto)
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
- [ ] Calculado a partir de la tasa de degradación de EP-02.3
- [ ] Considerar el compuesto de Q (piloto está obligado a usarlo si calificó en Q2+)
- [ ] Mostrar ventana óptima de pit stop como rango de vueltas

---

### F-03.2: Live strategy tracker durante la carrera
Durante la carrera, mostrar la estrategia de cada piloto en tiempo real.

**Vista:**
```
Strategy Tracker — Bahrain GP 2024 | LAP 23/57

Driver | Pos | Compound | Stint laps | Predicted pit | Status
VER    |  1  | HARD ●   |    +8      | Lap 35-40     | On track
HAM    |  2  | MEDIUM ● |   +13      | Lap 28-32     | On track
LEC    |  3  | SOFT ●   |   +23      | OVERDUE ⚠️    | On track  (pitting next lap?)
SAI    |  P  | —        |    —       | —             | IN PIT    (stop 1, lap 22)
...

● SOFT=red  ● MEDIUM=yellow  ● HARD=white  ● INTER=green  ● WET=blue
```

**Criterios de aceptación:**
- [ ] Integrado como pestaña adicional del live viewer (EP-00)
- [ ] Actualización en tiempo real con los datos de stints de OpenF1
- [ ] Marcar "OVERDUE" cuando el piloto lleva más vueltas que la ventana óptima

---

### F-03.3: Undercut/Overcut detector
Detectar y alertar cuando hay un undercut o overcut en curso.

**Detección:**
- **Undercut en curso**: piloto A entra a boxes mientras piloto B (delante) sigue en pista con neumático >15 vueltas de antigüedad
- **Overcut exitoso**: piloto que se queda en pista mientras el rival entra, y termina delante

**Alert:**
```
🟠 UNDERCUT ALERT [Lap 28]
NOR (P4) pitted → HARD (new)
Gap to ALO (P3, MEDIUM+19 laps): 2.1s
If NOR laps 0.5s faster for 3 laps, undercut succeeds
Predicted outcome: NOR P3 after pit stop window closes
```

**Criterios de aceptación:**
- [ ] Alerta visual en el live viewer cuando se detecta un undercut
- [ ] Post-carrera: listar todos los undercuts/overcuts detectados con resultado

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
- [ ] Alerta inmediata cuando OpenF1 reporta Safety Car en Race Control
- [ ] Cálculo automático de quién se beneficia de parar bajo SC
- [ ] Post-carrera: análisis de qué equipos capitalizaron el SC correctamente

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
