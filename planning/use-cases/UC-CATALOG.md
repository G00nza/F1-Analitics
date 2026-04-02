# Catálogo de Casos de Uso

## Iteración 0 — Live Session

### UC-01: Ver la clasificación en vivo
**Actor**: Fan viendo la Q3 en casa sin TV
**Trigger**: Qualifying está activo ahora mismo
**Flujo**:
1. Ejecuta `f1 live`
2. Sistema detecta la sesión activa via `session_key=latest`
3. Dashboard se abre con leaderboard actualizándose cada 2s
4. Ve posiciones, tiempos, sectores coloreados, neumáticos y clima
**Resultado**: Dashboard terminal en vivo con el mismo delay que la app oficial

---

### UC-02: Replay de una sesión que me perdí
**Actor**: Fan que estaba trabajando durante la FP2
**Trigger**: Quiere ver cómo evolucionó la práctica
**Flujo**:
1. `f1 live --session fp2 --replay --speed 10`
2. Sistema descarga (o usa cache) los datos completos de FP2
3. Reproduce el leaderboard como si fuera en vivo, a 10x velocidad
**Resultado**: Ve la práctica completa en minutos

---

### UC-03: Ver drill-down de un piloto durante la sesión
**Actor**: Fan siguiendo a Leclerc específicamente
**Trigger**: Quiere ver todos los tiempos de vuelta de LEC en Q3
**Flujo**:
1. En el dashboard live, presiona `d` + `LEC`
2. Vista cambia a detalle del piloto: todos sus tiempos, sectores, stint
**Resultado**: Vista detallada de LEC actualizada en tiempo real

---

### UC-04: Listar sesiones disponibles
**Actor**: Usuario nuevo que no sabe qué hay disponible
**Trigger**: Quiere explorar los datos disponibles
**Flujo**:
1. `f1 sessions`
2. Sistema lista los últimos 5 eventos con sus sesiones y estado (terminada/activa/futura)
**Resultado**: Lista de eventos y sesiones para elegir

---

## Iteración 1 — Weekend Analysis

### UC-05: Comparar rendimiento FP vs Qualifying
**Actor**: Analista técnico curioso
**Trigger**: "¿Quién guardó más rendimiento en las prácticas?"
**Flujo**:
1. `f1 weekend --round 5`
2. Sistema muestra tabla de tiempos FP1/FP2/FP3/Q por piloto
3. Columna de mejora FP1→Q para cada piloto
**Resultado**: Se ve que Verstappen solo mejoró 1.2s mientras Ferrari mejoró 2.5s

---

### UC-06: Analizar degradación de neumáticos en FP2
**Actor**: Fan interesado en estrategia antes de la carrera
**Trigger**: "¿Qué equipo degrada más los neumáticos?"
**Flujo**:
1. `f1 weekend --session fp2 --analysis degradation`
2. Sistema identifica long runs en FP2 por cada equipo
3. Muestra tasa de degradación por equipo y compuesto
**Resultado**: Red Bull degrada 0.06s/lap en HARD, Ferrari 0.11s/lap → McLaren probable 2-stop

---

### UC-07: Ver pace de carrera estimado desde FP
**Actor**: Jugador de Fantasy F1
**Trigger**: Quiere saber quién tiene mejor ritmo antes de elegir pilotos
**Flujo**:
1. `f1 weekend --analysis pace`
2. Sistema extrae long runs de FP2, descarta out-laps, calcula media por equipo
**Resultado**: Ranking de pace esperado en carrera

---

## Iteración 2 — Strategy & Race Day

### UC-08: Ver preview de estrategia antes de la carrera
**Actor**: Fan queriendo entender qué va a pasar
**Trigger**: La noche de sábado antes de la carrera
**Flujo**:
1. `f1 strategy preview`
2. Sistema muestra estrategias esperadas por piloto, ventana de pit stop, compuestos
**Resultado**: "VER esperado 1-stop SOFT→HARD, ventana pit laps 14-20"

---

### UC-09: Live strategy tracker durante la carrera
**Actor**: Fan viendo la carrera con análisis propio
**Trigger**: La carrera está en curso
**Flujo**:
1. `f1 live --view strategy` (o tecla `2` en el dashboard)
2. Vista muestra: posición, compuesto actual, vueltas en el stint, pit window, status
3. Cuando alguien pita, muestra el undercut alert si aplica
**Resultado**: Análisis de estrategia en tiempo real junto a la TV

---

### UC-10: Safety Car alert y análisis de impacto
**Actor**: Fan que no entiende la estrategia bajo SC
**Trigger**: Safety Car desplegado en vuelta 31
**Flujo**:
1. El dashboard muestra flash naranja: "🚗 SAFETY CAR — Lap 31"
2. Panel de impacto muestra quién debería parar y quién no
**Resultado**: "VER debe entrar AHORA (free stop). Si no entra, perderá posición."

---

## Iteración 3 — Current Season

### UC-11: Ver el estado del campeonato
**Actor**: Fan casual que no siguió las últimas carreras
**Trigger**: "¿Cómo está el campeonato?"
**Flujo**:
1. `f1 standings`
2. Sistema muestra clasificación con puntos, forma reciente y gap
**Resultado**: Standings actualizados con últimas 5 carreras de contexto

---

### UC-12: ¿Puede mi piloto ganar el campeonato?
**Actor**: Fan de Norris nervioso en noviembre
**Trigger**: "¿Todavía puede ganar Norris?"
**Flujo**:
1. `f1 championship simulate`
2. Sistema calcula escenarios matemáticos con carreras restantes
**Resultado**: "NOR necesita ganar las 2 restantes Y VER hacer ≤ P10 en ambas"

---

### UC-13: Preview antes del Gran Premio
**Actor**: Fan preparándose para el fin de semana
**Trigger**: Jueves antes del GP
**Flujo**:
1. `f1 preview` (o `f1` sin argumentos si no hay sesión activa)
2. Muestra: info del circuito, standings con puntos en juego, historial reciente
**Resultado**: Todo el contexto necesario para disfrutar el fin de semana
