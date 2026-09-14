# ZeroCloudAssist

Asistente técnico industrial **100 % offline** para móvil. El técnico pregunta en español sobre
un manual de equipo (en inglés) y recibe pasos concretos con cita de página. Sin nube, sin
coste por consulta, sin que la documentación salga del dispositivo.

**Estado:** prueba de concepto. Objetivo inmediato: validar que un LLM pequeño corre a
velocidad útil en un Samsung Galaxy A53 (6 GB RAM) antes de construir nada encima.

## Plan vigente

- [plan/01-zca-poc-hola-mundo.md](plan/01-zca-poc-hola-mundo.md): entorno Android, medición
  de modelos en el móvil y app mínima en Kotlin con llama.cpp. Criterios go/no-go incluidos.

Documentos de origen: [plan/zca-description.md](plan/zca-description.md),
[plan/zca-plan.md](plan/zca-plan.md), [plan/zca-config.md](plan/zca-config.md).

## Estructura

```
plan/        planes numerados y documentos de origen
manuales/    PDFs del equipo piloto (ABB ACS355), ignorados por git
models/      GGUF descargados, ignorados por git
informes/    mediciones y decisiones
docs/        entorno, versiones, notas técnicas
android/     app Android (etapa 4 del plan 01)
third_party/ llama.cpp como submódulo (etapa 3 del plan 01)
tools/       scripts Python con uv (plan 02)
```

## App Android (`android/`)

Hola mundo del plan 01: carga Qwen2.5-1.5B en el móvil con llama.cpp y responde en streaming,
sin red. Sin diseño a propósito (la estética va en el plan 02).

### Requisitos

| Qué | Versión |
| --- | --- |
| Android SDK | plataforma 36, build-tools 36 |
| NDK / CMake | 29.0.13113456 / 3.31.6 (las que fija el ejemplo de llama.cpp) |
| JDK para Gradle | 17 (Gradle 8.14.3 no arranca con el Java 25 de Android Studio) |
| Móvil | Android 13+ (API 33), arm64. Probado en Samsung A53 |
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

### Copiar el modelo

El GGUF va fuera del APK. Desde la raíz del repo, con la app ya instalada y abierta una vez:

```bash
adb push models/qwen2.5-1.5b-instruct-q4_k_m.gguf /sdcard/Android/data/com.ialogia.zerocloudassist/files/models/
```

Si falta, la app muestra este mismo comando en pantalla. Desde Git Bash, anteponer
`MSYS_NO_PATHCONV=1` (reescribe las rutas `/sdcard/...`); en PowerShell funciona tal cual.

### Leer métricas

```bash
adb logcat -s ZCA ZCA_METRICS
```

`ZCA` registra la carga y la liberación del modelo; `ZCA_METRICS` una línea por respuesta:
`carga 4,7 s · TTFT 0,8 s · 10,3 tok/s · heap 1417 MB · libre 1680 MB · 200 tokens`. La misma
línea (sin tokens) aparece en pantalla. Los hilos efectivos se ven en `adb logcat -s ai-chat`
(`init_context: Using 6 threads`).

Parámetros fijos (plan 01): 6 hilos, `n_ctx` 2048, temperatura 0,2, máximo 200 tokens, prompt de
sistema de §5. Preguntas de prueba en [docs/preguntas-protocolo.txt](docs/preguntas-protocolo.txt).

## Modelos (descarga manual, ver plan 01 §2A)

| Modelo | Fichero | Tamaño |
| --- | --- | --- |
| Qwen2.5-1.5B-Instruct Q4_K_M | `qwen2.5-1.5b-instruct-q4_k_m.gguf` | 1,12 GB |
| Llama 3.2 1B Instruct Q4_K_M | `Llama-3.2-1B-Instruct-Q4_K_M.gguf` | 808 MB |
| Qwen2.5-0.5B-Instruct Q4_K_M | `qwen2.5-0.5b-instruct-q4_k_m.gguf` | 491 MB |
