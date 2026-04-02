# EP-05: Temporada en Curso

**Iteración**: 3
**Prioridad**: Media
**Dependencias**: EP-01, EP-02, EP-03
**Estado**: Pendiente

## Descripción
Análisis del campeonato en curso: standings, forma reciente, historial en el circuito de la próxima carrera y contextualización del fin de semana actual dentro de la temporada.

---

## Features

### F-05.1: Standings actuales
Clasificación actualizada de pilotos y constructores.

**Comando**: `f1 standings` o `f1 standings --constructors`

**Output:**
```
Drivers Championship 2024 — after Round 22/24
P   Driver     Team        Points  Wins  Gap
1   VER (RBR)  Red Bull    393     8     —
2   NOR (MCL)  McLaren     331     3     -62
3   LEC (FER)  Ferrari     307     2     -86
4   SAI (FER)  Ferrari     259     1     -134
...

Recent form (last 5 races):
VER:  P1 P4 P1 P2 P1  → 63 pts
NOR:  P2 P1 P3 P1 P3  → 56 pts
```

**Criterios de aceptación:**
- [ ] Calculado desde los resultados de OpenF1 del año en curso
- [ ] Forma reciente (últimas 5 carreras) como columna adicional
- [ ] `f1 standings --after-round 15` muestra standings tras ronda específica

---

### F-05.2: Contexto previo al fin de semana
Antes del Gran Premio, ¿qué necesita saber el fan?

**Comando**: `f1 weekend preview`

**Contenido:**
- Próxima carrera: nombre, circuito, fecha
- Standings actuales con puntos en juego
- ¿Puede alguien cerrar el campeonato matemáticamente este fin de semana?
- Historial reciente en el circuito (últimas 2-3 ediciones si hay datos en OpenF1)
- Ganadores recientes en el circuito
- Estadísticas del circuito (vueltas, longitud, tipo)

**Output:**
```
━━━ NEXT RACE: GP de Las Vegas 2024 (Round 22) ━━━
📍 Las Vegas Strip Circuit | 50 laps | 309.959 km
📅 Sat Nov 23 → Race Sun Nov 24

Championship picture:
VER leads by 62 points. Can clinch this weekend if NOR scores ≤ X.
VER needs: ANY points (clinch scenarios exist)

Circuit history (OpenF1 data):
2023 Winner: VER (Red Bull) | Pole: LEC
Fastest lap record: 1:35.490 (VER, 2023)
```

**Criterios de aceptación:**
- [ ] Disponible automáticamente cuando no hay sesión activa
- [ ] Muestra "Next session starts in Xh Xm" si hay sesión programada

---

### F-05.3: Simulador de campeonato
¿Puede X ganar el campeonato si gana las próximas N carreras?

**Comando**: `f1 championship simulate`

**Funcionalidad:**
- Calcular puntos máximos posibles para cada piloto con las carreras restantes
- "Si NOR gana las 2 restantes + pole + fastest lap, ¿alcanza a VER?"
- Simular un resultado específico: `f1 championship simulate --round 23 --winner VER --p2 NOR`

**Output:**
```
Championship simulation — 2 races remaining

NOR can still win IF:
  - NOR wins both remaining races + gets fastest lap both times (50 pts)
  - VER scores 0 points in remaining races
  Max possible for NOR: 381 pts | VER current: 393 pts
  → NOR CANNOT WIN mathematically ✗

VER clinches championship if:
  - VER finishes P8 or better in Las Vegas (any NOR result)
  → Title clinch scenarios exist this weekend ✓
```

**Criterios de aceptación:**
- [ ] Cálculo de puntos máximos restantes por piloto
- [ ] Detección automática de cierre matemático del campeonato
- [ ] Acepta escenario hipotético via argumentos

---

### F-05.4: Forma reciente y tendencias
¿Quién llega en mejor momento al próximo GP?

**Comando**: `f1 form [--last N]`

**Métricas últimas N carreras:**
- Puntos acumulados (forma bruta)
- Posición media
- % de podios
- % de abandonos
- Posición media de qualifying

**Output:**
```
Driver form — last 5 races
Driver  | Pts | Avg pos | Podiums | DNFs | Avg Q
VER     |  63 |   1.6   |  5/5    |  0   |  1.4
NOR     |  56 |   2.4   |  4/5    |  0   |  1.8
LEC     |  36 |   4.2   |  2/5    |  1   |  2.2
```

**Criterios de aceptación:**
- [ ] N configurable (default: 5 últimas carreras)
- [ ] Excluye carreras donde el piloto no compitió

---

### F-05.5: Historial en el circuito
¿Cómo le fue históricamente a cada piloto en este circuito?

**Basado en datos disponibles en OpenF1 (2023+):**
- Resultados en las ediciones anteriores del GP
- Mejor vuelta, mejor posición, mejor qualifying

**Con datos Ergast (si se integra como complemento):**
- Historial extendido pre-2023

**Output:**
```
Circuit history — Bahrain International Circuit (2023–2024)
Driver  | 2023 Race | 2024 Race | Best lap
VER     |    P1     |    P1     | 1:31.344
SAI     |    P2     |    P2     | 1:31.602
NOR     |    P7     |    P4     | 1:31.901
```

**Criterios de aceptación:**
- [ ] Indica claramente cuántos años de datos hay
- [ ] Advertencia si hay menos de 2 ediciones disponibles

---

## Estimación de esfuerzo
- F-05.1 Standings: 1 día
- F-05.2 Weekend preview: 2 días
- F-05.3 Simulador campeonato: 2 días
- F-05.4 Forma reciente: 1 día
- F-05.5 Historial circuito: 1 día
- **Total**: ~7 días
