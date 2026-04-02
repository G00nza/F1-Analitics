# EP-09: Frontend IT-1 (Svelte + Charts)

**Iteración**: 1
**Prioridad**: Alta
**Dependencias**: EP-08 (IT-0 funcionando), EP-02 (weekend analysis backend)
**Estado**: Pendiente

## Descripción
Extiende el frontend Svelte (ya existente desde IT-0) con **Chart.js** y navegación multi-vista. No hay migración — el stack ya es Svelte + Vite desde EP-08.

---

## Por qué Svelte

- Bundle final muy pequeño (compila a JS vanilla, sin runtime)
- Reactivo por naturaleza: `$state` que actualiza el DOM automáticamente
- Mucho más simple que React para una persona sola construyendo el proyecto
- Ideal para dashboards con datos que cambian frecuentemente
- Vite da HMR (hot reload) instantáneo durante desarrollo

---

## Features

### F-09.1: Agregar Chart.js + rutas + estructura multi-vista

El setup de Svelte + Vite + integración Gradle ya existe desde EP-08. Este feature extiende la estructura con rutas y la carpeta de charts.

**Cambios en la estructura:**
```
frontend/src/
├── routes/                  ← NUEVO
│   ├── Live.svelte          ← /live  (leaderboard ya existe, se mueve aquí)
│   ├── Weekend.svelte       ← /weekend
│   └── Season.svelte        ← /season
└── components/
    └── charts/              ← NUEVO
        ├── LapTimeChart.svelte
        ├── DegradationChart.svelte
        ├── PositionChart.svelte
        └── GapChart.svelte
```

**Agregar Chart.js al package.json:**
```json
{
  "dependencies": {
    "chart.js": "^4.4.x",
    "chartjs-adapter-date-fns": "^3.x"
  }
}
```

**Criterios de aceptación:**
- [ ] Navegación multi-vista funciona sin recargar la página
- [ ] El store SSE persiste al navegar entre vistas (no reconecta)
- [ ] `./gradlew build` sigue produciendo JAR con frontend embebido

---

### F-09.2: Lap Time Chart
Gráfico de tiempos de vuelta a lo largo de una sesión.

**Qué muestra:**
- Eje X: número de vuelta
- Eje Y: tiempo de vuelta (en segundos o mm:ss.SSS)
- Una línea por piloto (coloreada con el color del equipo)
- Puntos huecos en out-laps (primer vuelta tras pit)
- Puntos especiales en la vuelta más rápida

**Interactividad:**
- Toggle de pilotos (click en la leyenda para mostrar/ocultar)
- Tooltip con detalle al hacer hover: vuelta, piloto, tiempo, compuesto
- Zoom en rango de vueltas (para analizar un stint)

**Casos de uso:**
- Ver la degradación de un stint específico
- Comparar el ritmo de dos pilotos en el mismo stint
- Identificar el efecto del tráfico (vuelta lenta por DRS train)

**Criterios de aceptación:**
- [ ] Default: muestra todos los pilotos, filtrable
- [ ] Pit stop marcado como discontinuidad en la línea (no conecta out-lap con in-lap)
- [ ] Performance: 60 vueltas × 20 pilotos = 1200 puntos, sin lag

---

### F-09.3: Position Chart (spaghetti chart)
El gráfico icónico de evolución de posiciones durante la carrera.

**Qué muestra:**
- Eje X: número de vuelta
- Eje Y: posición (1 arriba, 20 abajo)
- Una línea por piloto con color del equipo
- Eventos marcados: pit stops (punto), safety car (banda vertical)

**Casos de uso:**
- Ver remontadas épicas
- Ver el impacto del Safety Car en las posiciones
- Ver estrategias de una parada vs dos paradas

**Criterios de aceptación:**
- [ ] Safety Car period como banda vertical sombreada
- [ ] En vivo: la línea se extiende con cada nueva vuelta (animado)
- [ ] Funciona igual para carreras terminadas (usando cache)

---

### F-09.4: Degradation Chart
Para el análisis del fin de semana: ¿cuánto degradan los neumáticos?

**Qué muestra:**
- Un gráfico de líneas por equipo/piloto
- Eje X: número de vuelta dentro del stint (vuelta 1, 2, 3... de ese compuesto)
- Eje Y: tiempo de vuelta normalizado (delta respecto a la primera vuelta del stint)
- Pendiente de la línea = tasa de degradación

**Criterios de aceptación:**
- [ ] Selector de sesión: FP1 / FP2 / FP3
- [ ] Filtrar por compuesto (SOFT, MEDIUM, HARD)
- [ ] Solo mostrar stints de >5 vueltas (descartar runs cortos de Q simulation)

---

### F-09.5: Gap Chart
Evolución del gap al líder a lo largo de la carrera.

**Qué muestra:**
- El líder siempre en 0
- Los demás pilotos como líneas que oscilan alrededor del líder
- Pit stops como drops verticales (el piloto pierde tiempo en boxes)
- Recuperaciones como subidas

**Criterios de aceptación:**
- [ ] Claro cuando el piloto está en pit lane (línea punteada o segmento especial)
- [ ] Escala Y en segundos (no en posiciones)

---

### F-09.6: Navegación multi-vista
Navegación entre las distintas secciones del app.

**Rutas:**
```
/               → redirect a /live
/live           → Live session dashboard (leaderboard + charts)
/weekend        → Race weekend analysis (FP comparison, degradation)
/strategy       → Strategy view (stints, pit windows)
/season         → Championship standings + form
```

**Navegación:**
- Barra superior con las secciones principales
- En mobile: barra inferior (thumb-friendly)
- La ruta `/live` siempre muestra el badge "LIVE" si hay sesión activa

**Criterios de aceptación:**
- [ ] Navegación sin recarga de página (SPA routing con hash o History API)
- [ ] El estado SSE persiste al navegar entre vistas (no reconecta)
- [ ] Deep links funcionan: `http://192.168.1.x:8080/weekend` funciona directamente

---

### F-09.7: Weekend view
Vista de análisis del fin de semana usando los datos de EP-02.

**Layout:**
```
RACE WEEKEND — GP de Bahrain 2024
[FP1] [FP2] [FP3] [Q] ← tabs de sesión

Lap time progression table (todos los pilotos, mejora FP1→Q)
Degradation chart (FP2 long runs)
Estimated race pace ranking
```

**Criterios de aceptación:**
- [ ] Tabs de sesión, solo aparecen las sesiones ya terminadas
- [ ] La tabla de progresión se puede ordenar por cualquier columna
- [ ] Los charts responden a los filtros (tab seleccionado)

---

## Stack técnico
```json
{
  "dependencies": {
    "chart.js": "^4.4.x",
    "chartjs-adapter-date-fns": "^3.x"
  },
  "devDependencies": {
    "@sveltejs/vite-plugin-svelte": "^3.x",
    "svelte": "^4.x",
    "vite": "^5.x",
    "vitest": "^1.x"
  }
}
```

## Estimación de esfuerzo
- F-09.1 Setup Svelte + integración Gradle: 2 días
- F-09.2 Lap time chart: 2 días
- F-09.3 Position chart: 2 días
- F-09.4 Degradation chart: 1 día
- F-09.5 Gap chart: 1 día
- F-09.6 Navegación: 1 día
- F-09.7 Weekend view: 2 días
- **Total**: ~11 días
