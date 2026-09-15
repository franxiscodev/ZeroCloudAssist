# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Qué es y en qué punto está

Asistente técnico industrial 100 % offline para Android: el técnico pregunta en español sobre un
manual en inglés (piloto: variador ABB ACS355) y un LLM local responde. Hoy es el MVP del plan 02,
para una demo de 5 minutos en una charla: la app busca en el manual con un índice local (FTS5 y
vectores de multilingual-e5-small) y Qwen2.5-1.5B-Instruct Q4_K_M responde con llama.cpp en un
Samsung Galaxy A53. Todo local y gratuito: sin API keys ni servicios en la nube en ningún plan.

- Los planes aprobados viven en `plan/NN-zca-<tema>.md` y se ejecutan etapa a etapa. Leer el plan
  vigente antes de tocar nada. `plan/zca-*.md` son documentos de origen: no se editan.
- Las mediciones y decisiones van en `informes/AAAA-MM-DD-<tema>.md`. Las decisiones go/no-go
  (gates G1, G2…) son de Francisco: se recomienda, no se decide.
- Prompt de sistema de la app: plan 02 §6 y `rag/PromptBuilder.kt`. Si cambia, en los dos sitios.
  El protocolo del plan 01 (§5) sigue en `docs/preguntas-protocolo.txt` y `tools/calidad-pc.sh`,
  solo para medir modelos en el PC.
- Baterías de preguntas (`docs/bateria-manual.yaml`, `docs/bateria-control.yaml`) aprobadas por
  Francisco: no se cambian sin volver a aprobarlas. El glosario taller → manual
  (`docs/glosario-taller.yaml`) va dentro del índice.
- Temperatura 0,2 con semilla aleatoria: la misma pregunta da otro texto. Antes de nombrar la causa
  de una mala respuesta, repetirla (en el PC, con varias semillas contra el mismo GGUF).

## Comandos

Gradle necesita **JDK 17** (no arranca con el Java 25 de Android Studio). Solo hay `gradlew`
(sin `.bat`): lanzarlo con `bash` desde `android/`.

```bash
cd android
JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot" bash gradlew :app:testDebugUnitTest
JAVA_HOME=... bash gradlew :app:testDebugUnitTest --tests "com.ialogia.zerocloudassist.rag.SafetyRulesTest"
JAVA_HOME=... bash gradlew :app:assembleDebug        # APK en app/build/outputs/apk/debug/
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`android/local.properties` (ignorado) debe tener `sdk.dir=C:/Users/Francisco/AppData/Local/Android/Sdk`
con **barras normales**: con barras invertidas el build falla en segundos sin mensaje útil.

Móvil, desde la raíz del repo en PowerShell, con la app instalada y abierta una vez:

```powershell
tools\cargar-movil.ps1 -Serial <id>                  # modelos e índice fuera del APK, con SHA256
tools\estres-movil.ps1 -Preguntas 20 -Serial <id>    # guardia de estrés: preguntas seguidas por ADB
adb logcat -s ZCA ZCA_METRICS ZCA_RESPUESTA          # carga, búsquedas, métricas y respuestas (JSON)
adb logcat -s ai-chat                                # llama.cpp: "Using 6 threads", n_ctx, backend
```

Tras cada `adb install -r`: `adb shell pm uninstall --user 95 com.ialogia.zerocloudassist`.

Índice del manual (`tools/`, Python con uv; rutas relativas a la raíz del repo):

```bash
llama-server -m models/multilingual-e5-small-q8_0.gguf --embedding --pooling mean --port 8090
cd tools
uv run pytest -m "not manual"    # desde tools/: desde la raíz recoge los tests del submódulo
uv run zca-indice construir      # PDF → models/acs355.sqlite
uv run zca-indice evaluar        # recall@2 de FTS5, vectores e híbrida con la batería
```

En la consola de Windows con la salida redirigida, `evaluar` necesita `PYTHONIOENCODING=utf-8`.
`tools/calidad-pc.sh` lanza el protocolo del plan 01 contra los GGUF con un llama.cpp de Windows
(`C:\tools\llama.cpp`).

CI (`.github/workflows/ci.yml`): jobs `android-unit` (tests JVM de `app`) y `tools` (pytest sin los
tests `manual`), en cada PR y en `main`, los dos obligatorios en `main`. Si se añade o toca un test,
confirmar en el log del runner que sale `PASSED`.

## Arquitectura de `android/`

Dos módulos Gradle:

- **`lib`**: copia del módulo `lib` del ejemplo oficial `examples/llama.android` de llama.cpp
  (tag b10941), compilada contra el submódulo `third_party/llama.cpp` (`LLAMA_SRC` en
  `lib/src/main/cpp/CMakeLists.txt`). Se copió en vez de referenciar el del submódulo porque sus
  constantes no se pueden configurar desde Kotlin. Todos los cambios respecto al original llevan
  un comentario `ZeroCloudAssist:` (en `ai_chat.cpp`, `InferenceEngine.kt`,
  `InferenceEngineImpl.kt` y los ficheros nuevos `embedder.cpp` y `Embedder.kt`). Son tres tipos:
  - Constantes: 6 hilos, `n_ctx` 2048 y temperatura 0,2.
  - Añadidos: `resetConversation()`, que vuelve la caché KV al final del prompt de sistema, y el
    embedder de e5 (mismo `.so`, modelo y contexto propios). El embedder necesita que el motor de
    chat esté ya en `Initialized`, porque es él quien carga los backends de ggml.
  - Correcciones de fallos del ejemplo: el límite de tokens, el desplazamiento de contexto, el
    cierre de las respuestas cortadas, la liberación tras un error, `lastResponseTruncated` y el
    indicador de cancelación que dejaba puesto una liberación fallida.

  Lo demás se mantiene tal cual, incluidos los nombres de paquete `com.arm.aichat.*`, porque los
  símbolos JNI (`Java_com_arm_aichat_internal_...`) dependen de ellos. Buscar `ZeroCloudAssist:`
  antes de actualizar llama.cpp o de volver a copiar el ejemplo.
- **`app`** (`com.ialogia.zerocloudassist`, Compose):
  - `Assistant` es un `object` con vida de proceso que posee el `InferenceEngine` y todo el estado
    observable de la UI.
    - En `onStart` carga Qwen (con el prompt de sistema), e5 y el índice. En `onStop` libera los
      dos modelos; el índice se queda abierto y se reabre si cambian su fecha o su tamaño.
    - Por pregunta: `retrieve` (búsqueda híbrida), `SafetyRules`, `resetConversation()` y
      `sendUserPrompt` con `PromptBuilder.userTurn`.
    - Todas las operaciones del motor y del índice se serializan con un `Mutex`, porque
      `InferenceEngineImpl` lanza `IllegalStateException` en un estado inesperado y la conexión
      SQLite no admite hilos a la vez. `stop()` cancela el trabajo en curso antes de liberar.
  - `rag/`:
    - `QueryTerms` y `ManualSearch` son gemelos de `tools/zca_tools/terminos.py` y `evaluar.py`,
      con las mismas tablas de casos. Si cambia una regla, cambia en los dos y en el plan.
    - `ManualStore` abre el `.sqlite` en solo lectura con `androidx.sqlite:sqlite-bundled`, que
      trae FTS5. `MetaCheck` comprueba su `meta` (esquema y modelo de vectores).
    - `SafetyRules`: la tarjeta de seguridad la pone la app, no el modelo. Mira la pregunta, los
      fragmentos y el chunk anterior de cada fragmento (sin `dc bus`).
  - `ui/`: Compose con los tokens de `docs/diseno-ui.md` (`Theme.kt`) y fuentes OFL en `res/font`.
    `tools/estres-movil.ps1` busca el botón "Preguntar" por su texto.
  - Tests JUnit en `app/src/test` para `rag/`, `Conversation`, `Metrics`, `ModelLocation` y
    `MarkdownLite`. JNI, SQLite nativo y Compose solo se verifican en el móvil.
  - Sin memoria de conversación (MVP): cada pregunta parte del prompt de sistema con sus propios
    fragmentos. El hilo en pantalla es solo visual.

Detalles que no se ven leyendo un solo fichero:

- El backend CPU de ggml se carga en tiempo de ejecución desde `nativeLibraryDir`, eligiendo la
  variante según el SoC (`GGML_BACKEND_DL` + `GGML_CPU_ALL_VARIANTS`). Por eso el manifiesto de
  `app` lleva `android:extractNativeLibs="true"`: sin él, la carga falla.
- Con repack activo, los pesos de Qwen se copian a RAM anónima (~0,93 GB) y no quedan respaldados
  por mmap; e5 va con mmap. El heap nativo ronda 1,6 GB con los dos modelos cargados.
- **Compose BOM fijado en 2026.06.01**: 2026.08.00 y posteriores exigen AGP 9.1 y `compileSdk` 37,
  y el proyecto sigue en AGP 8.13.2 / SDK 36 como el ejemplo. Subir uno obliga a subir el otro.

## Entorno y trampas conocidas

- **MAX_PATH (260) en Windows**: el build nativo de KleidiAI genera rutas largas. `android/` cabe
  con ~36 caracteres de margen; el ejemplo compilado dentro de `third_party/` no cabe (el ejemplo
  sin tocar se compila desde un clon en `C:\tmp\zca-llama`). Si el repo se mueve a una ruta más
  larga, el build nativo fallará.
- **Android compila las expresiones regulares con ICU**, que no admite `(?U)`: la app se cerraba
  con los tests de la JVM en verde. `QueryTerms` usa fronteras de palabra explícitas.
- **Móvil por depuración inalámbrica**: el puerto se descubre con `adb mdns services`. El modo
  avión corta ADB, y al volver hay que reactivar "Depuración inalámbrica" a mano. El USB del A53 se
  cae; con USB y wifi a la vez, pasar `-s` con el serial de `adb devices`.
- **`adb logcat` espera al dispositivo sin límite** si se cae la conexión: en los scripts, toda
  orden de ADB con límite de tiempo, comprobando antes `adb devices`.
- **Git Bash reescribe las rutas `/sdcard/...`** en `adb push`/`adb shell`: usar PowerShell o
  anteponer `MSYS_NO_PATHCONV=1`.
- **PowerShell lee la salida de `adb` con la página de códigos de la consola**: guardada en un
  fichero, las tildes salen mal ("├ìndice"). Se recupera con `iconv -f utf-8 -t cp850`, o se lee
  desde Git Bash.
- `adb shell` junta los argumentos y la shell del móvil los vuelve a partir por los espacios: para
  `logcat -T`, la hora en segundos desde 1970 (`date +%s`).
- La app libera los modelos cada vez que pasa a segundo plano: salir a otra app (por ejemplo, a
  copiar una pregunta) cuesta una recarga de 6–8 s al volver.
- `ssh-agent` tiene arranque manual en esta máquina: `Start-Service ssh-agent` antes de
  `git fetch`/`pull`.
- El submódulo necesita `git config --global core.longpaths true` para clonarse en Windows.
- Samsung "apps duplicadas" (usuario 95) clona la app en cada instalación: sale un segundo icono
  sin modelo. Se quita con `adb shell pm uninstall --user 95 com.ialogia.zerocloudassist`.
- Para leer el estado de la pantalla del móvil, `uiautomator dump` es más fiable que las capturas.
  Nunca durante una generación o un benchmark: roba CPU y falsea los números.

Versiones exactas del SDK, NDK, CMake y del móvil, e incidencias de instalación, en
`docs/entorno-android.md`. SHA256 de los modelos en `docs/modelos.md`.
