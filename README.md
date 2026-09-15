# ZeroCloudAssist

Asistente técnico industrial **100 % offline** para móvil. El técnico pregunta en español sobre un
manual de equipo en inglés y recibe la respuesta con las páginas del manual de las que sale. Sin
nube, sin coste por consulta, sin que la documentación salga del dispositivo.

**Estado:** MVP del plan 02, para una demo de 5 minutos. En un Samsung Galaxy A53 de gama media
(2022), la app busca en el manual del variador ABB ACS355 con un índice local y responde con
Qwen2.5-1.5B sobre llama.cpp: primera palabra a los ~8 s y 9 tok/s de mediana. La app no pide el
permiso `INTERNET`. Resultados y decisión del gate G4 en
[informes/2026-09-15-rag-manual-a53.md](informes/2026-09-15-rag-manual-a53.md).

## Planes

- [plan/01-zca-poc-hola-mundo.md](plan/01-zca-poc-hola-mundo.md) (cerrado): entorno Android,
  medición de modelos en el móvil y app mínima con llama.cpp.
- [plan/02-zca-rag-manual.md](plan/02-zca-rag-manual.md): RAG sobre el manual. Índice en el PC,
  búsqueda en el móvil, pantalla de taller y medición.

Documentos de origen: [plan/zca-description.md](plan/zca-description.md),
[plan/zca-plan.md](plan/zca-plan.md), [plan/zca-config.md](plan/zca-config.md).

## Estructura

```
plan/        planes numerados y documentos de origen
manuales/    PDF del equipo piloto (ABB ACS355), ignorado por git
models/      GGUF, índice y datos de la charla, ignorados por git
informes/    mediciones y decisiones
docs/        entorno, modelos, batería de preguntas, glosario, diseño y licencias
android/     app Android: módulo lib (llama.cpp y e5) y módulo app (RAG y pantalla)
third_party/ llama.cpp como submódulo (tag b10941)
tools/       índice del manual (Python con uv), scripts para el móvil y página de la charla
```

## Cómo funciona

1. **En el PC**, `zca-indice construir` trocea el PDF en trozos de ~130 tokens, vectoriza cada
   trozo con multilingual-e5-small y lo guarda todo en `models/acs355.sqlite`: texto con FTS5,
   vectores y un glosario de palabras de taller a términos del manual.
2. **En el móvil**, cada pregunta se busca por palabras (FTS5, primero la entrada que define el
   código o el parámetro que se nombra) y por significado (e5). Las dos listas se fusionan (RRF) y
   entran en el prompt los 2 primeros trozos que caben en 300 tokens.
3. **Qwen responde en español** solo con esos trozos; cada pregunta es independiente. Si la
   pregunta o los trozos hablan de riesgo eléctrico, la app (no el modelo) muestra una tarjeta de
   seguridad que cita la página 18 del manual.

## App Android (`android/`)

### Requisitos

| Qué | Versión |
| --- | --- |
| Android SDK | plataforma 36, build-tools 36 |
| NDK / CMake | 29.0.13113456 / 3.31.6 (las que fija el ejemplo de llama.cpp) |
| JDK para Gradle | 17 (Gradle 8.14.3 no arranca con el Java 25 de Android Studio) |
| Móvil | Android 13+ (API 33), arm64. Probado en Samsung Galaxy A53 |
| Submódulo | `git submodule update --init` (llama.cpp en el tag b10941) |

Detalle de la instalación en [docs/entorno-android.md](docs/entorno-android.md).

### Compilar e instalar

```bash
cd android
echo "sdk.dir=C:/Users/<usuario>/AppData/Local/Android/Sdk" > local.properties   # barras normales
JAVA_HOME="<ruta al JDK 17>" bash gradlew :app:testDebugUnitTest :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

La primera compilación nativa tarda unos minutos y necesita red (CMake descarga KleidiAI). En
Windows, la ruta del repo no debe alargarse más de ~36 caracteres respecto a
`C:\MIOS\IAlogia\proyectos\ZeroCloudAssist`: el build nativo roza el límite de 260 (MAX_PATH).

En los Samsung con "apps duplicadas", cada instalación puede clonar la app (un segundo icono sin
modelos). Se quita con `adb shell pm uninstall --user 95 com.ialogia.zerocloudassist`.

### Copiar los modelos y el índice

Van fuera del APK. Desde la raíz del repo, en PowerShell, con la app instalada y abierta una vez:

```powershell
tools\cargar-movil.ps1      # -Serial <id de adb devices> si hay más de un dispositivo
```

Copia `models/qwen2.5-1.5b-instruct-q4_k_m.gguf` y `models/multilingual-e5-small-q8_0.gguf` a
`/sdcard/Android/data/com.ialogia.zerocloudassist/files/models/`, y `models/acs355.sqlite` a
`…/files/manuales/`. Solo copia lo que falta o ha cambiado, y comprueba el SHA256 en el móvil. Si
falta alguno, la app dice cuál y muestra el `adb push` exacto. Desde Git Bash, los `adb push` a
mano necesitan `MSYS_NO_PATHCONV=1` delante: Git Bash reescribe las rutas `/sdcard/...`.

### Leer métricas

```bash
adb logcat -s ZCA ZCA_METRICS ZCA_RESPUESTA
```

- `ZCA`: carga y liberación de los modelos, índice abierto y cada búsqueda (páginas y si hubo
  tarjeta de seguridad).
- `ZCA_METRICS`: una línea por respuesta, la misma que se ve en pantalla:
  `carga 5,8 s · TTFT 7,9 s · 9,2 tok/s · heap 1604 MB · libre 1379 MB · búsqueda 0,6 s · manual 210 tok · 36 tokens`.
  El TTFT cuenta desde que se pulsa "Preguntar", con la búsqueda dentro.
- `ZCA_RESPUESTA`: JSON con la pregunta, las páginas, la tarjeta, si se cortó y la respuesta.

`tools/estres-movil.ps1` hace 20 preguntas seguidas por ADB y resume métricas, fallos y memoria.

Parámetros: 6 hilos, `n_ctx` 2048, temperatura 0,2, máximo 400 tokens, manual de hasta 300 tokens
en 2 trozos. El prompt de sistema está en `rag/PromptBuilder.kt` (plan 02, §6). La semilla es
aleatoria: la misma pregunta puede dar otro texto.

## Índice del manual (`tools/`)

Necesita el PDF `manuales/EN_ACS355_UM_E_A5.pdf`, `models/multilingual-e5-small-q8_0.gguf` (se
convierte con `tools/convertir_e5.py`, ver [docs/modelos.md](docs/modelos.md)),
`models/qwen2.5-tokenizer.json` y `llama-server` de llama.cpp.

```bash
llama-server -m models/multilingual-e5-small-q8_0.gguf --embedding --pooling mean --port 8090   # en otra terminal
cd tools
uv run zca-indice construir     # → models/acs355.sqlite, en ~1 min
uv run zca-indice evaluar       # recall@2 de FTS5, vectores e híbrida con docs/bateria-manual.yaml
uv run pytest -m "not manual"   # lo mismo que la CI
```

Las rutas son relativas a la raíz del repo. En la consola de Windows con la salida redirigida,
`evaluar` necesita `PYTHONIOENCODING=utf-8` (imprime ✓ y ✗).

`tools/charla/` genera una página con respuestas reales del móvil para las charlas; sus datos van
a `models/charla/`, porque llevan texto del manual.

## CI

GitHub Actions en cada PR y en `main`: `android-unit` (tests JVM de la app) y `tools` (pytest sin
los tests `manual`, que necesitan el PDF y los modelos). Los dos son checks obligatorios de `main`.

## Modelos

| Modelo | Fichero | Tamaño |
| --- | --- | --- |
| Qwen2.5-1.5B-Instruct Q4_K_M | `qwen2.5-1.5b-instruct-q4_k_m.gguf` | 1,12 GB |
| multilingual-e5-small Q8_0 | `multilingual-e5-small-q8_0.gguf` | 132 MB |
| Tokenizador de Qwen2.5 (para contar tokens en el PC) | `qwen2.5-tokenizer.json` | 7 MB |

SHA256, origen y los modelos descartados en el plan 01, en [docs/modelos.md](docs/modelos.md).

## Licencias

Fuentes OFL de la app y uso del manual de ABB en [docs/licencias.md](docs/licencias.md). El
índice y los datos de la charla llevan texto del manual y no se versionan.
