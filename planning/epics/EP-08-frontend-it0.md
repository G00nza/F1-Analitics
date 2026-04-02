# EP-08: Frontend IT-0 (Svelte + Vite)

**Iteración**: 0
**Prioridad**: Alta
**Dependencias**: EP-06 (servidor SSE + REST funcionando)
**Estado**: Pendiente

## Descripción
Primera versión del frontend en **Svelte + Vite**: una SPA que muestra el live leaderboard actualizado via SSE. Funciona en PC, tablet y mobile desde el primer día.

**No hay charts en IT-0.** Solo la tabla de posiciones con la información más importante. Los charts se agregan en IT-1 (EP-09) sobre esta misma base Svelte.

---

## Wireframes

### Desktop
```
┌─────────────────────────────────────────────────────────────────────┐
│  🏎 F1 Analytics          [QUALIFYING Q3 • ACTIVE • 08:34]   🔴LIVE │
│  GP de Bahrain 2024 — Bahrain Int'l Circuit                         │
│  🌡 Air 28° Track 38° 💧 52% 💨 14 km/h NE    Last update: 14:23:07 │
├──────────────────────────────────────────────────────────────────────┤
│  P  #  Driver    Team        Best Lap    Gap       S1    S2    S3  Tyre│
│  1  1  VER       Red Bull    1:29.179   leader   25.8  32.1  31.2  🔴8 │
│  2 16  LEC       Ferrari     1:29.347   +0.168   25.9  32.2  31.2  🔴6 │
│  3  4  NOR       McLaren     1:29.617   +0.438   26.0  32.0  31.6  🔴5 │
│  ...                                                                    │
├──────────────────────────────────────────────────────────────────────┤
│  🟡 14:22:51  Yellow flag Sector 2 — Incident HAM Turn 14            │
│  🟢 14:20:03  Track clear                                            │
└──────────────────────────────────────────────────────────────────────┘
```

### Mobile (cards)
```
┌──────────────────────────────┐
│ 🏎 F1 Analytics    🔴 LIVE   │
│ QUALIFYING Q3 • 08:34 rem    │
│ 🌡 28°/38°  💧52%  💨14 km/h│
├──────────────────────────────┤
│ P1  1  VER  Red Bull         │
│         1:29.179   leader    │
│         🔴 SOFT • 8 laps     │
├──────────────────────────────┤
│ P2  16  LEC  Ferrari         │
│         1:29.347   +0.168    │
│         🔴 SOFT • 6 laps     │
├──────────────────────────────┤
│ P3   4  NOR  McLaren         │
│         1:29.617   +0.438    │
│         🔴 SOFT • 5 laps     │
└──────────────────────────────┘
```

---

## Features

### F-08.1: Setup Svelte + Vite + integración Gradle

**Estructura del frontend:**
```
frontend/
├── src/
│   ├── App.svelte
│   ├── lib/
│   │   ├── sse.js          ← EventSource connection
│   │   └── f1utils.js      ← formateo de tiempos, colores de equipos
│   ├── stores/
│   │   └── session.js      ← Svelte stores (sessionState, connectionStatus)
│   └── components/
│       ├── Header.svelte
│       ├── Leaderboard.svelte
│       ├── DriverRow.svelte
│       ├── TyreIndicator.svelte
│       ├── RaceControlBar.svelte
│       └── IdleView.svelte
├── vite.config.js
└── package.json
```

**Vite config con proxy al backend:**
```javascript
// frontend/vite.config.js
export default {
  plugins: [svelte()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080'
    }
  }
}
```

**Integración con Gradle:**
```kotlin
// server/build.gradle.kts
val buildFrontend = tasks.register<Exec>("buildFrontend") {
    workingDir = file("../frontend")
    commandLine("npm", "run", "build")
    inputs.dir("../frontend/src")
    outputs.dir("../frontend/dist")
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(buildFrontend)
    from("../frontend/dist") { into("static") }
}
```

**Workflow de desarrollo:**
```bash
# Terminal 1: backend con hot reload
./gradlew -t :app:classes
./gradlew :app:run

# Terminal 2: frontend con HMR
cd frontend && npm run dev
# → http://localhost:5173 (HMR activo, proxy a :8080)
```

**Criterios de aceptación:**
- [ ] `./gradlew build` produce JAR con frontend embebido sin pasos manuales
- [ ] `npm run dev` conecta al backend en `:8080` via proxy
- [ ] Cambios en `.svelte` se reflejan en el browser sin refresh (HMR)

---

### F-08.2: Conexión SSE y store de estado

```javascript
// stores/session.js
import { writable } from 'svelte/store';

export const sessionState = writable(null);
export const connectionStatus = writable('connecting');  // 'connecting' | 'connected' | 'reconnecting'

const es = new EventSource('/api/events/live');

es.addEventListener('session_state', (e) => {
    sessionState.set(JSON.parse(e.data));
});

es.addEventListener('session_status', (e) => {
    // Maneja IDLE, session starting soon, etc.
    const status = JSON.parse(e.data);
    if (status.status === 'IDLE') sessionState.set(null);
});

es.onerror = () => connectionStatus.set('reconnecting');
es.onopen  = () => connectionStatus.set('connected');
```

**Criterios de aceptación:**
- [ ] Reconexión automática cuando se cae la conexión (nativo del browser)
- [ ] Indicador visual "LIVE" (verde parpadeante) / "RECONNECTING" (naranja)
- [ ] No hay polling manual — todo llega por SSE

---

### F-08.3: Tabla de leaderboard responsive

**Columnas desktop:**
| P | # | Driver | Team | Best Lap | Gap | S1 | S2 | S3 | Tyre |

**Columnas tablet (ocultar sectores):**
| P | # | Driver | Team | Best Lap | Gap | Tyre |

**Mobile (cards verticales):**
Cada piloto es una card con: posición, número, código, equipo, mejor vuelta, gap, compuesto.

**Colores (CSS variables para fácil theming):**
```css
:root {
    --color-best-sector: #BF5FFF;    /* morado: mejor sector global */
    --color-personal-best: #00C853;  /* verde: mejor personal */
    --color-normal: #FFFFFF;
    --color-slow: #FF5252;           /* rojo: tiempo lento */

    --tyre-soft: #FF1E00;
    --tyre-medium: #FFF200;
    --tyre-hard: #EBEBEB;
    --tyre-inter: #39B54A;
    --tyre-wet: #0067FF;

    --bg-primary: #15151E;           /* fondo oscuro estilo F1 TV */
    --bg-secondary: #1F1F2E;
    --text-primary: #FFFFFF;
    --text-secondary: #AAAAAA;
}
```

**Criterios de aceptación:**
- [ ] Actualización de la tabla sin parpadeo (Svelte actualiza solo los nodos cambiados)
- [ ] Animación suave cuando un piloto cambia de posición (0.3s transition)
- [ ] Fondo oscuro por defecto (estilo F1 TV)
- [ ] El piloto en pit lane se muestra con fondo más oscuro y texto gris

---

### F-08.4: Header de sesión

**Contenido:**
- Nombre del GP y del circuito
- Tipo de sesión: FP1 / FP2 / FP3 / Q / R y su estado: ACTIVE / ENDED / UPCOMING
- Tiempo restante (si está disponible) o tiempo transcurrido
- Condiciones climáticas: temperatura aire/pista, humedad, viento, icono de lluvia si `rainfall=true`
- Indicador de conexión SSE

**Criterios de aceptación:**
- [ ] "ACTIVE" con punto verde parpadeante cuando la sesión está en curso
- [ ] "ENDED" cuando la sesión terminó
- [ ] Si no hay sesión activa: "Next session: FP2 — Sunday 10:30 local"
- [ ] Temperatura de pista > 50°C: advertencia visual (afecta degradación)

---

### F-08.5: Panel de Race Control

**Diseño:**
- Fijo al fondo de la pantalla, máximo 3-4 mensajes visibles
- Nuevo mensaje aparece con animación de fade-in
- Colores por tipo: 🟡 amarillo, 🔴 rojo, 🟢 verde, 🚗 naranja (SC)
- En mobile: colapsable (tap para expandir)

**Criterios de aceptación:**
- [ ] Safety Car: banner naranja prominente arriba de la tabla
- [ ] Bandera roja: banner rojo con texto "RED FLAG — SESSION SUSPENDED"
- [ ] Los últimos 5 mensajes visibles, resto scrollable

---

### F-08.6: Página de "no hay sesión activa"

Estado idle del servidor — componente `IdleView.svelte`.

```
┌─────────────────────────────────┐
│         🏎 F1 Analytics         │
│                                 │
│   No hay sesión activa ahora.   │
│                                 │
│   Próximo evento:               │
│   GP de Miami 2024              │
│   FP1 — Viernes 3 Mayo 12:30    │
│                                 │
│   [Ver última sesión]           │
└─────────────────────────────────┘
```

**Criterios de aceptación:**
- [ ] Muestra el próximo evento si hay datos disponibles
- [ ] Botón para ver la última sesión terminada
- [ ] La página sigue conectada via SSE — cuando empieza una sesión, muestra el leaderboard automáticamente

---

## Stack
```json
{
  "devDependencies": {
    "@sveltejs/vite-plugin-svelte": "^3.x",
    "svelte": "^4.x",
    "vite": "^5.x"
  }
}
```

Sin Chart.js en IT-0. Los charts se agregan en EP-09 (IT-1) sin necesidad de migrar el stack.

---

## Estimación de esfuerzo
- F-08.1 Setup Svelte + Vite + Gradle: 1.5 días
- F-08.2 SSE store: 0.5 días
- F-08.3 Leaderboard + responsive: 2 días
- F-08.4 Header de sesión: 1 día
- F-08.5 Race Control panel: 1 día
- F-08.6 Estado idle: 0.5 días
- **Total**: ~6.5 días
