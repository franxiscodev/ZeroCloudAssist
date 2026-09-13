# Plan 01 — ZeroCloudAssist: PoC del LLM en el Samsung Galaxy A53

Aprobado el 2026-09-13. Los tres ficheros previos de `plan/` se conservan como documentos de
origen.

---

## 1. Contexto y por qué este plan es go/no-go

ZeroCloudAssist es un asistente técnico 100 % offline: el técnico pregunta en español sobre un
manual industrial en inglés y recibe pasos con cita de página. Piloto: variador ABB ACS355. El
plan original era iOS; se cambió a un **Samsung Galaxy A53 5G (6 GB RAM, Exynos 1280: 2×A78 +
6×A55, Mali-G68)** desarrollando desde Windows 11.

**De este PoC depende seguir o desistir.** El riesgo que decide es uno solo: si un LLM útil corre
a velocidad aceptable en un móvil de gama media. Todo lo demás (RAG, UI, informes) se construye
encima y no tiene sentido sin esto. Por eso este plan mide antes de construir, construye lo mínimo,
y fija de antemano qué número significa "vale" y qué número significa "paramos".

### Estado del PC (verificado 2026-09-13)

| Recurso | Estado |
| --- | --- |
| Windows 11 Pro, i7-11800H (16 hilos), 15,6 GB RAM, 364 GB libres en C: | OK |
| `git 2.51`, `gh` autenticado como franxiscodev (ssh), `docker`, `winget`, `scoop` | OK |
| `uv 0.7.9` | OK (se usa en el plan 02, no aquí) |
| Android Studio, SDK, NDK, CMake, `adb` | **No instalado** |
| Java | Solo Java 8 (irrelevante: Android Studio trae su JDK) |
| Manuales ACS355 | Ya en `manuales/` (guía rápida 2,6 MB + manual usuario 11 MB) |

### Fuentes verificadas (2026-09-13)

| Recurso | URL | Dato |
| --- | --- | --- |
| Qwen2.5-1.5B-Instruct GGUF oficial | `huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF` | `qwen2.5-1.5b-instruct-q4_k_m.gguf`, 1,12 GB, sin gating |
| Llama 3.2 1B Instruct GGUF (unsloth) | `huggingface.co/unsloth/Llama-3.2-1B-Instruct-GGUF` | `Llama-3.2-1B-Instruct-Q4_K_M.gguf`, 808 MB, sin gating |
| Qwen2.5-0.5B-Instruct GGUF oficial | `huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF` | `qwen2.5-0.5b-instruct-q4_k_m.gguf`, 491 MB |
| PocketPal AI | Google Play / `github.com/a-ghorbani/pocketpal-ai` | MIT, gratuita, carga GGUF local, página Benchmark con tok/s y memoria |
| llama.cpp ejemplo Android | `github.com/ggml-org/llama.cpp/tree/master/examples/llama.android` | Módulos `app` y `lib`; `lib/src/main/cpp/CMakeLists.txt` activa KleidiAI y OpenMP en arm64-v8a; `minSdk = 33` |
| llama.cpp docs Android | `docs/android.md` del repo | Flags de cross-compilación: `ANDROID_ABI=arm64-v8a`, `ANDROID_PLATFORM=android-28`, `GGML_OPENMP=OFF`, `GGML_LLAMAFILE=OFF` |
| Android Studio | `winget install --id Google.AndroidStudio` | versión 2026.1.4.7 en winget |

---

## 2. Decisiones cerradas

| Tema | Decisión | Por qué |
| --- | --- | --- |
| Plataforma | Android nativo, **Kotlin + Jetpack Compose** | llama.cpp trae ejemplo oficial Android en Kotlin; Flutter añadiría FFI y otra capa de fallos |
| Motor | `llama.cpp` como **submódulo git** en `third_party/llama.cpp`, fijado a **tag**, compilado con NDK/CMake | Reproducible; sin binarios de terceros |
| Modelo base | **Qwen2.5-1.5B-Instruct Q4_K_M** (1,12 GB) | Buen español, cabe en 6 GB con margen |
| Fallbacks (en orden) | Llama 3.2 1B Q4_K_M → Qwen2.5-0.5B Q4_K_M | Cada uno ~30 % más rápido que el anterior, a costa de calidad |
| Sin 3B | Descartado | El móvil reporta ~2 GB disponibles: el 3B (2 GB solo de pesos) no cabe |
| Modelo fuera del APK | Se copia por `adb push` a la carpeta externa de la app | APK de 1 GB hace el ciclo compilar-instalar inviable |
| Contexto | `n_ctx = 2048`, `max tokens = 200` | Suficiente para el hola mundo, limita RAM y batería |
| Hilos | Empezar con `n_threads = 4`; medir también 2 y 6 | El Exynos 1280 tiene 2 núcleos grandes y 6 pequeños; el óptimo hay que medirlo |
| UI del hola mundo | Cero diseño | La estética "instrumento de taller" va con mockup en el plan 02 |
| Repo | Uno solo: `android/`, `third_party/`, `tools/` (vacío), `plan/`, `manuales/`, `informes/`, `docs/` | Un PoC de una persona no justifica varios repos |
| Git | Política global: rama desde `main`, Conventional Commits, **push y merge de Francisco** | — |

---

## 2b. Presupuesto de memoria (el A53 reporta ~2 GB disponibles de 6)

| Componente | Qwen 1.5B Q4_K_M | Llama 3.2 1B Q4_K_M | Qwen 0.5B Q4_K_M |
| --- | --- | --- | --- |
| Pesos (mmap, páginas reclamables) | 1,12 GB | 0,81 GB | 0,49 GB |
| Caché KV a n_ctx 2048 | ~60 MB | ~130 MB | ~50 MB |
| Buffers de cómputo + app + Compose | 150–300 MB | 150–300 MB | 150–300 MB |
| **Total estimado** | **~1,5 GB** | **~1,2 GB** | **~0,8 GB** |
| Margen sobre 2 GB disponibles | ~0,5 GB (justo) | ~0,8 GB (cómodo) | ~1,2 GB (sobrado) |

Reglas:

- **Los pesos van siempre por mmap** (comportamiento por defecto de llama.cpp; `use_mlock`
  OFF). Android puede descartar y releer esas páginas bajo presión: se ralentiza, pero no mata.
- **Gate de memoria en 1.9:** `MemAvailable` en reposo, sin apps abiertas. Si es < 1,8 GB, el
  1.5B se prueba igual pero el 1B pasa a ser candidato principal.
- **Primera medida ante presión:** bajar `n_ctx` a 1024, no cambiar de modelo.
- **Segunda medida:** cerrar apps en segundo plano y desactivar apps de Samsung que no se usen.
- **Si el 1.5B provoca cierre por Low Memory Killer o cae bajo el umbral de velocidad por
  swapping de páginas (síntoma: tok/s inestable, muy distinto entre ejecuciones), el 1B es el
  modelo base.** Sin discusión: la demo no puede depender de que el móvil esté recién reiniciado.
- PocketPal muestra memoria en su Benchmark: se anota siempre el pico, no solo la velocidad.

## 3. Criterios go/no-go (fijados antes de medir)

Medidos **en modo avión**, con el móvil a más del 50 % de batería, sin cargar, tras 2 minutos
de reposo, en la app externa y después en la app propia. Cada métrica: **3 ejecuciones, se
anota la mediana**.

| Métrica | GO | GO con reservas | NO-GO |
| --- | --- | --- | --- |
| Generación (tok/s) | ≥ 8 | 5 – 8 | < 5 |
| Tiempo hasta primer token con prompt de ~150 tokens | ≤ 2 s | 2 – 4 s | > 4 s |
| Carga del modelo (frío) | ≤ 10 s | 10 – 20 s | > 20 s |
| Respuesta en español correcto y con pasos numerados (3 prompts fijos) | 3/3 | 2/3 | ≤ 1/3 |
| App sobrevive minimizar 60 s y volver | Sí | — | No |

Reglas de decisión:

1. Si Qwen 1.5B da NO-GO en velocidad, se pasa a Llama 3.2 1B. Si también, a Qwen 0.5B.
2. Si el 0.5B es el único que pasa velocidad pero falla calidad de español, **el proyecto se
   replantea**: o se acepta un móvil de gama superior, o se cambia la propuesta. Esa decisión es
   de Francisco y se documenta en `informes/`.
3. "GO con reservas" permite seguir al plan 02 pero obliga a reservar tiempo de optimización
   (hilos, flags, KleidiAI) antes de la demo.

---

## 4. Etapas, pasos y verificación

Tiempos estimados asumiendo descargas a velocidad normal. El día 1 es casi todo instalar.

### Etapa 0 — Repositorio Git y GitHub (30 min)

**Rama:** el commit inicial va en `main`. Es la única excepción: sin él no existe base desde la
que ramificar. Desde ahí, todo por rama y PR.

| # | Paso | Comando / detalle | Verificación |
| --- | --- | --- | --- |
| 0.1 | Inicializar | `git init -b main` en `C:\MIOS\IAlogia\proyectos\ZeroCloudAssist` | `git status` responde |
| 0.2 | `.gitignore` **antes** del primer commit | Ignorar: `*.gguf`, `models/`, `manuales/*.pdf`, `android/build/`, `android/**/build/`, `android/.gradle/`, `android/local.properties`, `android/.idea/`, `*.sqlite`, `.env` | `git status` no lista PDFs |
| 0.3 | `README.md` mínimo | Qué es, estado (PoC), enlace a `plan/01-…` | — |
| 0.4 | Copiar este plan | `plan/01-zca-poc-hola-mundo.md` | — |
| 0.5 | Commit inicial en `main` | `chore: bootstrap del repositorio con plan 01` | `git log --oneline` = 1 commit |
| 0.6 | Crear repo remoto | `gh repo create ZeroCloudAssist --private --source . --remote origin` (**sin** `--push`) | `git remote -v` muestra origin |
| 0.7 | **Francisco** | `git push -u origin main` | `git fetch --prune && git status` = al día |
| 0.8 | Protección de `main` | `gh api -X PUT repos/franxiscodev/ZeroCloudAssist/branches/main/protection` con PR requerida y `enforce_admins: true` | En privado gratuito dará **403**: se comunica y se ofrecen las tres salidas (público / Pro / hook local). Mientras, la política global es lo único que protege `main` |
| 0.9 | Rama de trabajo | `git switch -c chore/entorno-android` | Etapas 1 y 2 commitean aquí (solo docs e informes) |

### Etapa 1 — Entorno Android en Windows (2–3 h, casi todo descarga)

| # | Paso | Comando / detalle | Verificación |
| --- | --- | --- | --- |
| 1.0 | Permisos del entorno | Skill `update-config`: permitir `adb`, `gradlew`, `winget`, `logcat` en `.claude/settings.json` del repo | Sin prompt en cada `adb devices` |
| 1.1 | Android Studio | `winget install --id Google.AndroidStudio --exact` | Arranca; asistente inicial completado con SDK por defecto |
| 1.2 | SDK Manager → SDK Platforms | Android 14 (API 34) | — |
| 1.3 | SDK Manager → SDK Tools | **NDK (Side by side)**, **CMake**, Android SDK Platform-Tools, Build-Tools | Existen `%LOCALAPPDATA%\Android\Sdk\ndk\<ver>` y `...\cmake\<ver>` |
| 1.4 | Variables de entorno de usuario | `ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk`; añadir `%ANDROID_HOME%\platform-tools` al PATH | Nueva terminal: `adb version` responde |
| 1.5 | A53: opciones de desarrollador | Ajustes → Acerca del teléfono → Información de software → tocar 7 veces "Número de compilación" | Aparece "Opciones de desarrollador" |
| 1.6 | A53: depuración USB | Opciones de desarrollador → Depuración USB ON. También "Mantener pantalla activa" ON | — |
| 1.7 | Cable y autorización | Conectar; en el móvil aceptar "Permitir depuración USB" marcando "Permitir siempre" | `adb devices` → `<serial>  device` (no `unauthorized`, no `offline`) |
| 1.8 | Versión de Android y API | `adb shell getprop ro.build.version.release` y `getprop ro.build.version.sdk` | **API ≥ 33** exigida por el ejemplo de llama.cpp. Si es menor: actualizar el A53 por OTA (A53 llega a Android 14/15). Si no se puede, bajar `minSdk` a 28 en el ejemplo (no usa APIs modernas) |
| 1.9 | RAM real y libre (**gate de memoria**, ver §2b) | `adb shell cat /proc/meminfo \| head -3` con el móvil en reposo y sin apps abiertas | Anotar `MemTotal` y `MemAvailable`. Esperado ~5,5 GB total y ~2 GB disponibles. Si `MemAvailable` < 1,8 GB, el 1B pasa a candidato principal |
| 1.10 | Espacio | `adb shell df -h /sdcard` | ≥ 5 GB libres para modelos |
| 1.11 | Documentar | `docs/entorno-android.md` con versiones instaladas (Studio, SDK, NDK, CMake, Android del móvil) | Commit `docs: entorno android verificado` |

#### Fallos típicos y qué hacer

- `adb devices` vacío: cambiar cable (muchos son solo de carga), cambiar puerto USB, instalar
  "Samsung USB Driver for Mobile Phones" desde la web de Samsung, revocar autorizaciones USB en el
  móvil y volver a conectar.
- `unauthorized`: el diálogo del móvil no se aceptó. Desconectar, `adb kill-server`, reconectar.
- Si `adb` o `gh` se cuelgan sin error: skill `git-doctor`.

### Etapa 2 — Medir el modelo sin escribir código (1 día)

Objetivo: número de tok/s y calidad de español **antes** de tocar Kotlin. Si aquí sale NO-GO,
no se pierde tiempo en el toolchain.

#### 2A. Descargar modelos en el PC (30 min, según red)

| # | Paso | Detalle |
| --- | --- | --- |
| 2A.1 | Carpeta local ignorada | `models/` en la raíz del repo (está en `.gitignore`) |
| 2A.2 | Descargar Qwen 1.5B | `huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf` (1,12 GB) |
| 2A.3 | Descargar fallbacks (mismo día, evita esperar luego) | Llama 3.2 1B Q4_K_M (808 MB) y Qwen 0.5B Q4_K_M (491 MB) |
| 2A.4 | Verificar | Tamaños coinciden con los de la tabla de fuentes. Guardar SHA256 de cada uno en `docs/modelos.md` |

#### 2B. Calidad en el PC, separada de la velocidad (1 h)

Sirve para no confundir "el móvil es lento" con "el modelo responde mal". Se usa el mismo motor.

| # | Paso | Detalle |
| --- | --- | --- |
| 2B.1 | Descargar llama.cpp para Windows | Release `llama-<build>-bin-win-cpu-x64.zip` (o equivalente AVX2) desde `github.com/ggml-org/llama.cpp/releases`. Descomprimir en `C:\tools\llama.cpp\` (fuera del repo) |
| 2B.2 | Lanzar los 3 prompts fijos (ver §5) con cada modelo | `llama-cli -m models\<gguf> -c 2048 -n 200 -t 8 --temp 0.2 -p "<prompt>"` con el system prompt de §5 |
| 2B.3 | Puntuar calidad | Criterio de §5. Anotar en el informe |

Si Qwen 1.5B falla calidad en el PC, el móvil no lo va a arreglar: se prueba directamente el
siguiente.

#### 2C. Velocidad y calidad en el A53 con PocketPal AI (2 h)

| # | Paso | Detalle | Verificación |
| --- | --- | --- | --- |
| 2C.1 | Instalar PocketPal AI desde Google Play | Última versión | Abre |
| 2C.2 | Copiar los GGUF al móvil | `adb push models\qwen2.5-1.5b-instruct-q4_k_m.gguf /sdcard/Download/` (y los otros dos) | `adb shell ls -la /sdcard/Download/*.gguf` con tamaños correctos |
| 2C.3 | En PocketPal: Models → + → local storage → seleccionar GGUF | Cargar | Modelo aparece como cargado |
| 2C.4 | **Modo avión ON**, Wi-Fi y Bluetooth OFF | — | Icono de avión visible |
| 2C.5 | Ajustes del modelo en PocketPal | `n_ctx 2048`, `n_threads 4`, `temp 0.2`, `max tokens 200` | — |
| 2C.6 | Benchmark integrado de PocketPal | 3 ejecuciones | Anotar tok/s de prompt processing y de generación, y memoria |
| 2C.7 | Chat con los 3 prompts fijos de §5 | 3 ejecuciones de cada, cronometrar TTFT a mano si la app no lo da | Anotar tok/s que muestra la app, TTFT, calidad |
| 2C.8 | Repetir 2C.6 con `n_threads 2` y `6` | — | Elegir el mejor; anotar |
| 2C.9 | Repetir 2C.5–2C.8 con Llama 1B y Qwen 0.5B | Aunque el 1.5B pase: tener la curva completa es lo que se enseña a un cliente | — |
| 2C.10 | Aplicar §3 | — | **Decisión GO / GO con reservas / NO-GO escrita** |
| 2C.11 | Informe y cierre de rama | `informes/2026-MM-DD-medicion-modelos-a53.md` con la plantilla de §6. Skill `finishing-a-development-branch` | Commit `docs: medición de modelos en A53`. PR de `chore/entorno-android` abierto. Francisco fusiona |

**Gate G1:** no se pasa a la etapa 3 sin un modelo con GO o GO con reservas y el informe fusionado.

### Etapa 3 — Toolchain validado: compilar el ejemplo oficial sin tocarlo (medio día)

Antes de escribir nuestra app, se compila e instala el ejemplo de llama.cpp **tal cual**. Si eso
falla, el problema es del entorno, no de nuestro código, y se aísla ahí.

**Rama:** `feat/hola-mundo-llm` desde `main` actualizado (`git fetch --prune`, verificar
`main == origin/main`).

| # | Paso | Detalle | Verificación |
| --- | --- | --- | --- |
| 3.1 | Submódulo | `git submodule add https://github.com/ggml-org/llama.cpp third_party/llama.cpp`; `cd third_party/llama.cpp && git checkout <último tag bNNNN>`; commit `build: añade llama.cpp como submódulo (tag bNNNN)` | `git submodule status` muestra el tag |
| 3.2 | Abrir en Android Studio | `File → Open → third_party/llama.cpp/examples/llama.android` | Gradle sync OK. Si pide NDK/CMake de versión concreta, instalarla desde SDK Manager (la que fija su `build.gradle.kts`) |
| 3.3 | Compilar `arm64-v8a` | `Build → Make Project` | `lib` compila con KleidiAI ON (mensaje en log de CMake) |
| 3.4 | Instalar en el A53 | `Run` con el A53 seleccionado | La app arranca |
| 3.5 | Modelo | Seguir el README del ejemplo para indicar la ruta del GGUF (o `adb push` a la ruta que espera) | Carga sin crash |
| 3.6 | Medir | Mismo prompt corto de §5 | tok/s en el mismo orden que PocketPal (±30 %). Si es mucho menor: revisar hilos y flags antes de seguir |
| 3.7 | Documentar | Añadir a `docs/entorno-android.md`: versión NDK/CMake usada, tiempo de compilación, incidencias | Commit `docs: ejemplo oficial llama.android compilado y medido` |

#### Fallos típicos

- CMake no encuentra el NDK: `local.properties` con `ndk.dir`, o fijar `ndkVersion` en Gradle.
- Error de descarga de KleidiAI (CMake `FetchContent`): necesita red en tiempo de compilación;
  si hay proxy/cortafuegos, compilar una vez con red y cachear.
- Crash al cargar el modelo: ruta incorrecta o permisos de almacenamiento. Usar siempre la
  carpeta externa de la app (`/sdcard/Android/data/<id>/files/`), que no requiere permisos.
- Cualquier fallo no listado: skill `systematic-debugging`, no parchear a ciegas.

### Etapa 4 — Hola mundo propio en Kotlin (1–2 días)

**Misma rama** `feat/hola-mundo-llm`.

| # | Paso | Detalle | Verificación |
| --- | --- | --- | --- |
| 4.1 | Crear `android/` | Copiar la estructura del ejemplo: módulo `lib` (JNI + CMake) y módulo `app`. `applicationId = com.ialogia.zerocloudassist`, `minSdk` según 1.8, solo `abiFilters += "arm64-v8a"` | Gradle sync OK |
| 4.2 | CMake de `lib` | `LLAMA_SRC` apunta a `../../../../../third_party/llama.cpp`. Mantener KleidiAI ON, OpenMP ON como en el ejemplo; `GGML_LLAMAFILE=OFF` | Compila |
| 4.3 | Capa nativa | Reutilizar el JNI del ejemplo (cargar modelo, crear contexto, enviar prompt, streaming token a token, liberar). **No** reescribirlo | Funciona igual que en 3.6 |
| 4.4 | Chat template | Usar `llama_chat_apply_template` con el system prompt de §5 para que Qwen responda en español y con pasos | Respuestas con formato |
| 4.5 | UI Compose mínima | `TextField`, botón "Generar", `Text` con salida en streaming, y una fila de métricas | Nada más en pantalla |
| 4.6 | Métricas (**TDD**) | Clase pura `Metrics` con test JUnit primero: tok/s = tokens / (t_fin − t_primer_token), TTFT, formato. Luego la app la alimenta con carga (ms), TTFT (ms), tok/s, `Debug.getNativeHeapAllocatedSize()` y `ActivityManager.MemoryInfo.availMem`. Se muestran y se escriben en `Log.i("ZCA_METRICS", …)` | `gradlew :app:testDebugUnitTest` verde; `adb logcat -s ZCA_METRICS` las muestra |
| 4.7 | Ruta del modelo (**TDD**) | Función pura `modelPath(filesDir, name)` y `adbPushHint(applicationId, name)` con test primero. Ruta: `context.getExternalFilesDir(null)/models/<nombre>.gguf`. Si no existe, la pantalla muestra el `adb push` exacto | Test verde; mensaje correcto sin modelo |
| 4.8 | Copiar modelo | `adb push models\<gguf> /sdcard/Android/data/com.ialogia.zerocloudassist/files/models/` | La app lo carga |
| 4.9 | Ciclo de vida | Liberar contexto y modelo en `onStop`; recargar en `onStart`. Cancelar generación en curso con `abort_callback` | Minimizar 60 s y volver: sin crash ni ANR |
| 4.10 | Límites | `n_ctx 2048`, `max tokens 200`, `n_threads` = el mejor de 2C.8 | — |
| 4.11 | Modo avión | Repetir los 3 prompts de §5, 3 veces, mediana | Métricas dentro de ±30 % de PocketPal |
| 4.12 | `README.md` | Requisitos, cómo compilar, cómo copiar el modelo, cómo leer métricas por logcat | Alguien con Android Studio lo reproduce |
| 4.13 | `CLAUDE.md` del repo | Skill `init` | — |
| 4.14 | Informe | `informes/2026-MM-DD-hola-mundo-a53.md` con la plantilla de §6, comparando con 2C | — |
| 4.15 | Revisión, commits y PR | Commits atómicos `build:` / `feat:` / `test:` / `docs:`. Antes del PR: `/code-review` sobre el diff y skill `receiving-code-review` para cada hallazgo. Luego `finishing-a-development-branch`: `gh pr create` con título y cuerpo, resumen y comando de push para Francisco | Hallazgos resueltos o justificados; PR abierto; Francisco fusiona |
| 4.16 | Tag | `git tag -a v0.1.0-hola-mundo -m "…"`. **Francisco** `git push --follow-tags` | Tag en GitHub |
| 4.17 | Lecciones | Skill `lecciones-aprendidas` con lo que costó | Informe en su repo |
| 4.18 | Skill propia (condicional) | Si hubo ≥ 3 incidencias repetibles en 3.x–4.x: `writing-skills` para crear `zca-android` (copiar modelo, leer métricas, recuperar de LMK) | Skill probada en una ejecución limpia |

**Gate G2:** cierre del plan 01. Decisión GO/NO-GO definitiva escrita en el informe de 4.14.

---

## 5. Protocolo de prueba fijo

**System prompt** (igual en PC, PocketPal y app propia):

> Eres un asistente técnico industrial. Responde siempre en español, de forma breve, con pasos
> numerados cuando haya que hacer algo. Si hay riesgo eléctrico, avisa primero.

**Prompts** (`temp 0.2`, `max tokens 200`):

1. Corto: *"¿Qué es un variador de frecuencia?"*
2. Técnico: *"Un variador ABB muestra el fallo F0009 y el armario está muy caliente. ¿Qué reviso?"*
3. Seguridad: *"¿Cómo mido la tensión del bus de continua del variador?"*

**Calidad (por prompt, 0–2 puntos):** 2 = español correcto, pasos numerados, sin inventar
datos concretos del manual; 1 = español correcto pero sin estructura o con alguna invención;
0 = mezcla idiomas, se corta, o alucina claramente. Pasa con **≥ 5 de 6**. (El modelo no tiene
el manual: no se penaliza que no sepa la causa exacta de F0009, sí que se la invente con
seguridad.)

---

## 6. Plantilla de informe (`informes/`)

```text
# Medición <qué> en Samsung A53 — <fecha>
Móvil: modelo, Android, API, MemTotal, MemAvailable en reposo, batería %, modo avión: sí
Herramienta: PocketPal <ver> | app propia <commit> | llama-cli <build>
Modelo: nombre, cuantización, tamaño, SHA256
Parámetros: n_ctx, n_threads, temp, max tokens

| Prompt | Ejecución | Carga (s) | TTFT (s) | tok/s | Calidad (0-2) | Notas |
(3 filas por prompt + fila mediana)

Hilos: tabla tok/s para 2 / 4 / 6
Memoria: pico observado
Incidencias: cierres, ANR, calentamiento
Decisión según §3: GO / GO con reservas / NO-GO — y por qué
```

---

## 7. Fuera de este plan (plan 02)

RAG con SQLite + FTS5 + `sqlite-vec`, embeddings `multilingual-e5-small`, chunks de 150–200
palabras con página, Python con `uv` en `tools/`, pantalla de chat con estética "instrumento de
taller" (mockup previo con skill `design`), CI en GitHub Actions, empaquetado del modelo.

---

## 8. Skills y herramientas: cuáles, cuándo y cuáles no

Todas las disponibles en este entorno, revisadas una a una. Las que entran tienen paso asignado.

### 8.1 Entran en este plan

| Skill / herramienta | Cuándo (paso) | Para qué |
| --- | --- | --- |
| `superpowers:brainstorming` | Hecho (esta conversación) | Definir alcance, decisiones y umbrales antes de planificar |
| `superpowers:writing-plans` | Hecho (este documento) | Este plan es su salida, adaptado a la carpeta `plan/` numerada |
| `superpowers:executing-plans` | Toda la ejecución | Llevar el plan etapa a etapa con checkpoint humano en G1 y G2 |
| `superpowers:test-driven-development` | 4.5–4.7 | Lo que tiene lógica pura se hace con test primero: cálculo de tok/s y TTFT, resolución de la ruta del modelo, mensaje de `adb push`. JNI y Compose no: solo se prueban en el móvil |
| `superpowers:verification-before-completion` | Cada salida de etapa y antes de cada PR | "Verificado" = corrió en el A53 y hay números anotados; nunca "compila" |
| `superpowers:systematic-debugging` | Ante cualquier fallo | NDK, CMake, JNI, LMK, `adb`. Prohibido parchear a ciegas: cada intento de compilación son minutos |
| `superpowers:requesting-code-review` + `/code-review` | 4.15, antes de `gh pr create` | Revisión del diff de la app propia (ciclo de vida, liberación de memoria, hilos) |
| `superpowers:receiving-code-review` | Tras 4.15 si la revisión devuelve hallazgos | Verificar cada hallazgo antes de aplicarlo, no aceptar en bloque |
| `superpowers:finishing-a-development-branch` | 2C.11 y 4.15 | Cerrar rama: PR, resumen, comando de push para Francisco |
| `git-doctor` | Cuando `adb`, `git` o `gh` se cuelguen o pidan credenciales | Diagnóstico por síntomas; no adivinar |
| `update-config` / `fewer-permission-prompts` | Inicio de la etapa 1 | Permitir `adb`, `gradlew`, `winget` y lecturas de logcat sin prompt en cada llamada |
| `context7` (MCP) | 3.1, 4.2–4.4 | API vigente de llama.cpp (JNI del ejemplo, `llama_chat_apply_template`, flags CMake) para la versión del tag fijado |
| `run` | 4.8–4.11 | Lanzar la app en el A53 y leer `logcat -s ZCA_METRICS` de forma repetible |
| `init` | 4.13 | `CLAUDE.md` del repo con cómo compilar, medir y copiar el modelo |
| `lecciones-aprendidas` | 4.17 y tras cualquier atasco > 1 h | Informe en su repo (`lecciones-aprendidas` va directo a `main`, es la excepción de la política) |
| `superpowers:writing-skills` / `skill-creator` | Después de 4.17, si hubo ≥ 3 incidencias repetibles | Crear skill `zca-android` con: copiar modelo por adb, leer métricas, recuperar de LMK. Solo con dolor real documentado, no antes |

### 8.2 Quedan fuera, y por qué

| Skill / herramienta | Por qué no |
| --- | --- |
| `design` | Va en el plan 02. El hola mundo no tiene diseño por decisión: medir antes de vestir |
| `dataviz` | Los informes llevan tablas, no gráficos. Con 3 modelos × 3 hilos una tabla se lee mejor. Si en el plan 02 hace falta una gráfica para cliente, se usa entonces |
| `superpowers:using-git-worktrees` | Una persona, una rama activa a la vez. El worktree añadiría una copia del submódulo de llama.cpp (cientos de MB) sin beneficio |
| `superpowers:subagent-driven-development` | Las etapas son secuenciales y dependen del móvil físico conectado. No hay tareas paralelizables |
| `superpowers:dispatching-parallel-agents` | Mismo motivo |
| `Workflow` / `workflow-authoring` | Orquestación multiagente para un PoC de 4 etapas lineales es coste sin retorno |
| `security-review` | No hay red, secretos ni entrada externa en el hola mundo. Entra en el plan 02 si se añade sincronización o carga de ficheros del usuario |
| `simplify` | Se aplica a diffs propios; el grueso de la etapa 4 es código reutilizado del ejemplo. Se valora en el plan 02 |
| `claude-api` | No se usa la API de Claude: todo es local y sin coste. Revisar solo si en el futuro se añade un modo online |
| `artifact-design`, `artifact-diagramming`, `artifact-capabilities` | No se publica ninguna página. Los informes son Markdown en el repo |
| `loop`, `schedule` | Nada que vigilar periódicamente: no hay CI todavía (plan 02) ni procesos largos desatendidos |
| `keybindings-help`, `statusline-setup` | Configuración del editor, ajena al proyecto |
| Agentes `Explore` / `Plan` | El repo está vacío; no hay código que explorar. Se reconsidera en el plan 02 cuando exista `android/` y `tools/` |

---

## 9. Verificación final del plan

1. `adb devices` lista el A53 autorizado; `docs/entorno-android.md` con versiones.
2. Informe de medición con 3 modelos, 3 prompts, 3 ejecuciones, hilos 2/4/6, y decisión escrita.
3. Ejemplo oficial de llama.cpp compilado e instalado desde este PC.
4. App propia en modo avión responde a los 3 prompts en español y muestra las 4 métricas.
5. Minimizar 60 s y volver no la mata.
6. `git ls-files | Select-String -Pattern 'gguf|pdf'` vacío. `main` solo tiene el commit
   inicial más merges de PR.
7. Tag `v0.1.0-hola-mundo` en GitHub.
8. Decisión GO/NO-GO firmada en el informe de la etapa 4.

## 10. Riesgos

| Riesgo | Mitigación |
| --- | --- |
| A53 con Android < 13 (API < 33) | Paso 1.8: OTA o bajar `minSdk` |
| Descargas pesadas (Studio ~1 GB + SDK/NDK ~3 GB + modelos ~2,5 GB) | Todo el día 1; descargar los 3 modelos de una vez |
| El ejemplo `llama.android` cambia entre tags | Submódulo fijado a tag; leer su README de esa versión, no de memoria |
| KleidiAI requiere red al compilar | Compilar una vez con red |
| Solo ~2 GB RAM disponibles (dato del móvil) | Presupuesto de §2b: mmap, `n_ctx` 2048→1024, liberar en `onStop`, 1B como base si el 1.5B se cierra o es inestable; sin 3B |
| Calor y throttling en medición | Reposo 2 min entre series; anotar temperatura si la app la da |
| Protección de `main` no disponible | Decisión de Francisco (público / Pro / hook local) |
| El único modelo que pasa velocidad falla calidad | Regla 2 de §3: replanteo documentado, no se sigue a ciegas |
