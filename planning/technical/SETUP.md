# Setup inicial del proyecto

## Requisitos del entorno

```bash
# JVM
java --version        # 17+ recomendado (Kotlin 2.x)
./gradlew --version   # 8.x

# Python (para el bridge)
python3 --version     # 3.10+
pip install -r bridge/requirements.txt

# Node.js (para el frontend IT-1+)
node --version        # 20+
npm --version         # 10+
```

---

## Hot reload — requisito obligatorio del setup

Los cambios en el código deben reflejarse en el servidor en ejecución **sin reinicio manual**. Aplica a los tres componentes.

### 1. Backend Kotlin (Ktor)

Ktor tiene modo desarrollo con auto-reload de clases via `ApplicationEngineEnvironment`:

```kotlin
// app/src/main/kotlin/Main.kt
fun main() {
    embeddedServer(CIO, port = 8080, host = "0.0.0.0",
        watchPaths = listOf("classes", "resources")   // observa cambios en el classpath
    ) {
        configureServer()
    }.start(wait = true)
}
```

```kotlin
// app/build.gradle.kts
ktor {
    development = true   // activa el auto-reload en Gradle
}
```

**Flujo de desarrollo backend:**
```bash
./gradlew -t :app:classes   # recompila clases automáticamente al guardar
# (en otra terminal)
./gradlew :app:run           # el servidor detecta los nuevos .class y recarga
```

O en IntelliJ: **Run → Edit Configurations → On frame deactivation: Update classes and resources**.

**Limitación conocida**: el JVM hot-swap solo recarga cambios de métodos, no adiciones de clases nuevas ni cambios de estructura. Para esos casos sí hay que reiniciar. Con [JRebel](https://www.jrebel.com/) se supera esta limitación, pero es pago. Para el uso normal del día a día, el hot-swap de Ktor cubre la mayoría de los casos.

---

### 2. Frontend (Svelte + Vite — desde IT-0)

Vite tiene HMR nativo. El dev server de Vite corre en el puerto 5173 y hace proxy al backend Ktor:

```javascript
// frontend/app/vite.config.js
export default {
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',        // REST API
      '/api/events': {
        target: 'http://localhost:8080',
        changeOrigin: true                    // SSE funciona sin configuración especial
      }
    }
  }
}
```

**Flujo de desarrollo:**
```bash
# Terminal 1: backend Kotlin
./gradlew -t :app:classes
./gradlew :app:run

# Terminal 2: frontend Svelte
cd frontend && npm run dev
# → abre http://localhost:5173 (HMR activo)
# → la API va a http://localhost:8080 via proxy
```

Cambios en `.svelte`, `.js` o `.css` se reflejan en el browser sin refresh.

---

### 4. Bridge Python

El bridge no tiene hot reload — si cambia `bridge.py`, hay que reiniciarlo. Como el bridge raramente cambia una vez estable, no es un problema en la práctica.

Para desarrollo del bridge, iniciarlo manualmente:
```bash
python3 bridge/bridge.py 9001
```

---

## Variables de entorno de desarrollo

```bash
# .env.local (no commitear)
KTOR_DEVELOPMENT=true       # activa auto-reload y logs detallados
BRIDGE_PORT=9001
SERVER_PORT=8080
LOG_LEVEL=DEBUG
STORE_TELEMETRY=true
```

---

## Primer arranque

```bash
# 1. Instalar dependencias Python
pip install -r bridge/requirements.txt

# 2. Compilar y correr (arranca bridge + Ktor automáticamente)
./gradlew :app:run

# → Ktor en http://localhost:8080
# → Bridge Python en ws://localhost:9001
# → Abrir http://localhost:8080 en el browser
```

Para desarrollo con HMR (IT-1+):
```bash
# Terminal 1
./gradlew -t :app:classes & ./gradlew :app:run

# Terminal 2
cd frontend/app && npm run dev
# → abrir http://localhost:5173
```
