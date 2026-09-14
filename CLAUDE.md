# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Qué es y en qué punto está

Asistente técnico industrial 100 % offline para Android: el técnico pregunta en español sobre un
manual en inglés (piloto: variador ABB ACS355) y un LLM local responde. Hoy es una prueba de
concepto: la app carga Qwen2.5-1.5B-Instruct Q4_K_M con llama.cpp en un Samsung Galaxy A53 y
responde en streaming. Todo local y gratuito: sin API keys ni servicios en la nube en ningún plan.

- Los planes aprobados viven en `plan/NN-zca-<tema>.md` y se ejecutan etapa a etapa. Leer el plan
  vigente antes de tocar nada. `plan/zca-*.md` son documentos de origen: no se editan.
- Las mediciones y decisiones van en `informes/AAAA-MM-DD-<tema>.md`. Las decisiones go/no-go
  (gates G1, G2…) son de Francisco: se recomienda, no se decide.
- Protocolo fijo de prueba (prompt de sistema y 3 preguntas): plan 01 §5, copiado en
  `docs/preguntas-protocolo.txt`, `tools/calidad-pc.sh` y `Assistant.kt`. Si cambia, cambiarlo en
  los tres sitios.

## Comandos

Gradle necesita **JDK 17** (no arranca con el Java 25 de Android Studio). Solo hay `gradlew`
(sin `.bat`): lanzarlo con `bash` desde `android/`.

```bash
cd android
JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot" bash gradlew :app:testDebugUnitTest
JAVA_HOME=... bash gradlew :app:testDebugUnitTest --tests "com.ialogia.zerocloudassist.ConversationTest"
JAVA_HOME=... bash gradlew :app:assembleDebug        # APK en app/build/outputs/apk/debug/
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`android/local.properties` (ignorado) debe tener `sdk.dir=C:/Users/Francisco/AppData/Local/Android/Sdk`
con **barras normales**: con barras invertidas el build falla en segundos sin mensaje útil.

Modelo (fuera del APK, desde la raíz del repo, con la app instalada y abierta una vez):

```bash
adb push models/qwen2.5-1.5b-instruct-q4_k_m.gguf /sdcard/Android/data/com.ialogia.zerocloudassist/files/models/
adb logcat -s ZCA ZCA_METRICS          # carga/liberación y una línea de métricas por respuesta
adb logcat -s ai-chat                  # log de llama.cpp: "Using 6 threads", n_ctx, backend elegido
```

`tools/calidad-pc.sh` lanza el protocolo de §5 contra los GGUF en el PC con un llama.cpp de
Windows (`C:\tools\llama.cpp`) y guarda las salidas en `informes/<fecha>-calidad-pc/`.

## Arquitectura de `android/`

Dos módulos Gradle:

- **`lib`**: copia del módulo `lib` del ejemplo oficial `examples/llama.android` de llama.cpp
  (tag b10941), compilada contra el submódulo `third_party/llama.cpp` (`LLAMA_SRC` en
  `lib/src/main/cpp/CMakeLists.txt`). Se copió en vez de referenciar el del submódulo porque sus
  constantes no se pueden configurar desde Kotlin. Cambios respecto al original, marcados con
  comentario `ZeroCloudAssist:` en `ai_chat.cpp`: 6 hilos, `n_ctx` 2048, temperatura 0,2 y el
  cálculo del límite de tokens corregido. Lo demás se mantiene tal cual, incluidos los nombres de
  paquete `com.arm.aichat.*`, porque los símbolos JNI (`Java_com_arm_aichat_internal_...`)
  dependen de ellos.
- **`app`** (`com.ialogia.zerocloudassist`, Compose):
  - `Assistant` es un `object` con vida de proceso que posee el `InferenceEngine` y todo el
    estado observable de la UI. Carga en `onStart` y libera en `onStop`. Todas las operaciones del
    motor se serializan con un `Mutex`, porque `InferenceEngineImpl` lanza `IllegalStateException`
    si se le llama en un estado inesperado (liberar mientras genera, cargar antes de `Initialized`…).
    `stop()` cancela el trabajo en curso antes de liberar.
  - `Conversation`, `Metrics` y `ModelLocation` son lógica pura con tests JUnit en `app/src/test`.
    JNI y Compose solo se verifican en el móvil.
  - El modelo nativo guarda el historial en la caché KV. Al liberar se pierde, y la UI añade un
    aviso `Notice`.

Detalles que no se ven leyendo un solo fichero:

- El backend CPU de ggml se carga en tiempo de ejecución desde `nativeLibraryDir`, eligiendo la
  variante según el SoC (`GGML_BACKEND_DL` + `GGML_CPU_ALL_VARIANTS`). Por eso el manifiesto de
  `app` lleva `android:extractNativeLibs="true"`: sin él, la carga falla.
- Con repack activo, los pesos se copian a RAM anónima (~0,93 GB) y no quedan respaldados por mmap:
  el heap nativo ronda 1,4 GB con el modelo cargado.
- **Compose BOM fijado en 2026.06.01**: 2026.08.00 y posteriores exigen AGP 9.1 y `compileSdk` 37,
  y el proyecto sigue en AGP 8.13.2 / SDK 36 como el ejemplo. Subir uno obliga a subir el otro.

## Entorno y trampas conocidas

- **MAX_PATH (260) en Windows**: el build nativo de KleidiAI genera rutas largas. `android/` cabe
  con ~36 caracteres de margen; el ejemplo compilado dentro de `third_party/` no cabe (el ejemplo
  sin tocar se compila desde un clon en `C:\tmp\zca-llama`). Si el repo se mueve a una ruta más
  larga, el build nativo fallará.
- **Móvil por depuración inalámbrica**: el puerto se descubre con `adb mdns services`. El modo
  avión corta ADB, y al volver hay que reactivar "Depuración inalámbrica" a mano.
- **Git Bash reescribe las rutas `/sdcard/...`** en `adb push`/`adb shell`: usar PowerShell o
  anteponer `MSYS_NO_PATHCONV=1`.
- `ssh-agent` tiene arranque manual en esta máquina: `Start-Service ssh-agent` antes de
  `git fetch`/`pull`.
- El submódulo necesita `git config --global core.longpaths true` para clonarse en Windows.
- Samsung "apps duplicadas" (usuario 95) puede clonar la app instalada: sale un segundo icono sin
  modelo. Se quita con `adb shell pm uninstall --user 95 com.ialogia.zerocloudassist`.
- Para leer el estado de la pantalla del móvil, `uiautomator dump` es más fiable que las capturas.
  Nunca durante una generación o un benchmark: roba CPU y falsea los números.

Versiones exactas del SDK, NDK, CMake y del móvil, e incidencias de instalación, en
`docs/entorno-android.md`. SHA256 de los modelos en `docs/modelos.md`.
