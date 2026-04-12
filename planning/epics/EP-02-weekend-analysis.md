# EP-02: Análisis de Fin de Semana

**Iteración**: 1
**Prioridad**: Alta
**Dependencias**: EP-01, EP-00 (datos de al menos 2 sesiones del mismo fin de semana)
**Estado**: Finzalizada

## Descripción
Ver cómo evolucionó el rendimiento de cada piloto y equipo a través del fin de semana completo: FP1 → FP2 → FP3 → Qualifying → Carrera. Identificar quién llegó al sábado con el setup correcto, quién mejoró, quién sorprende.

---

## Features

### F-02.1: Resumen del fin de semana
Vista integrada de todas las sesiones del fin de semana en una sola pantalla.

**Comando**: `f1 weekend` o `f1 weekend --year 2024 --round 5`

**Output:**
```
━━━━━ GRAN PREMIO DE MÓNACO 2024 — Race Weekend Summary ━━━━━

Sesiones:                FP1      FP2      FP3      Q        R
VER (RBR)  Posición:     P2       P1       P1       P1       P1
           Best lap:     1:11.2   1:10.8   1:10.1   1:09.721  —
           Gap:          +0.189   leader   leader   leader    —

LEC (FER)  Posición:     P1       P3       P2       P2       P2
           Best lap:     1:11.0   1:11.1   1:10.3   1:09.892  —
           Gap:          leader   +0.312   +0.198   +0.171    —
...
```

**Criterios de aceptación:**
- [x] Muestra solo sesiones con datos disponibles
- [x] Colores para destacar mejor posición por piloto en cada sesión
- [x] Funciona con fin de semana en curso (parcialmente completo)

---

### F-02.2: Progresión de tiempos a lo largo del fin de semana
¿Cuánto mejoró cada piloto desde FP1 hasta Qualifying?

**Métricas:**
- Delta de mejor lap entre sesiones: `FP1→FP2: -0.412s`, `FP2→FP3: -0.223s`, etc.
- Mejora total FP1→Quali
- Quién mejoró más (identificar los que "guardaron más" en las prácticas)
- Quién no mejoró (potencial problema de setup)

**Output:**
```
Lap time progression — Bahrain GP 2024
Driver  | FP1        | FP2        | FP3        | Q          | Δ FP1→Q
VER     | 1:32.215   | 1:31.887   | 1:31.134   | 1:29.179   | -3.036s ↓↓
NOR     | 1:33.012   | 1:32.100   | 1:31.556   | 1:29.617   | -3.395s ↓↓
HAM     | 1:32.800   | 1:32.650   | 1:32.100   | 1:29.982   | -2.818s ↓
```

**Criterios de aceptación:**
- [x] Alinea tiempos por sesión type (no por timestamp)
- [x] Distingue simulación de clasificación vs ritmo de carrera en FP (difícil automáticamente — marcar con advertencia)

---

### F-02.3: Análisis de degradación de neumáticos en FP
En las prácticas libres se puede ver cuánto degradan los neumáticos.

**Metodología:**
- Identificar stints de más de 5 vueltas en FP (simulación de carrera)
- Calcular la tasa de degradación: pendiente del tiempo de vuelta a lo largo del stint
- Comparar degradación entre compuestos y entre equipos

**Output:**
```
Tyre degradation — FP2 long runs
Driver  | Compound | Stint laps | Lap 1     | Lap 15    | Deg/lap
VER     | HARD     | 18         | 1:33.102  | 1:34.287  | +0.079s/lap
HAM     | HARD     | 20         | 1:33.450  | 1:34.890  | +0.072s/lap
LEC     | MEDIUM   | 15         | 1:32.801  | 1:34.512  | +0.114s/lap
```

**Criterios de aceptación:**
- [x] Filtrar out-laps e in-laps del cálculo
- [x] Separar claramente runs cortos (qualifying sim) de runs largos (race sim)
- [x] Disponible solo para sesiones con datos de stints (OpenF1 2023+)

---

### F-02.4: Comparativa de sectores entre sesiones
¿En qué parte del circuito ganó tiempo cada piloto de una sesión a otra?

**Output:**
```
Sector improvement — VER, FP3 vs Qualifying
Sector  | FP3    | Q       | Delta
S1      | 26.218 | 25.891  | -0.327 ↓ (mejor)
S2      | 32.445 | 32.109  | -0.336 ↓
S3      | 24.471 | 23.179  | -1.292 ↓↓ (mayor mejora)
TOTAL   | 83.134 | 81.179  | -1.955 ↓
```

**Criterios de aceptación:**
- [x] Compara siempre el best lap de cada sesión
- [x] Identifica el sector donde más mejoró y donde menos mejoró

---

### F-02.5: Pace comparativo entre equipos en FP
¿Quién tiene el mejor ritmo de carrera basándose en los long runs de FP?

**Métricas:**
- Pace promedio de los long runs por equipo (con degradación extraída)
- Ranking de pace esperado de carrera basado en FP2
- Gap entre equipos en pace puro (sin tráfico)

**Output:**
```
Estimated race pace — based on FP2 long runs
Rank | Team      | Avg lap (adj) | Gap to best
  1  | Red Bull  | 1:33.410     | —
  2  | Ferrari   | 1:33.712     | +0.302
  3  | McLaren   | 1:33.890     | +0.480
  4  | Mercedes  | 1:34.101     | +0.691
```

**Criterios de aceptación:**
- [x] Advertencia clara: "estimado basado en FP, condiciones pueden variar"
- [x] Excluir vueltas con SC, banderas amarillas

---

## Estimación de esfuerzo
- F-02.1 Resumen fin de semana: 2 días
- F-02.2 Progresión de tiempos: 1 día
- F-02.3 Degradación neumáticos: 2 días
- F-02.4 Comparativa sectores: 1 día
- F-02.5 Race pace desde FP: 2 días
- **Total**: ~8 días
