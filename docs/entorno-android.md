# Entorno Android en Windows

Plan 01, etapa 1. Instalado el 2026-09-13. Todo por línea de comandos, sin pasar por el
asistente gráfico de Android Studio.

## Versiones instaladas en el PC

| Componente | Versión | Cómo se instaló |
| --- | --- | --- |
| Android Studio | 2026.1.4 (Quail 4), build AI-261.26222.65 | `winget install --id Google.AndroidStudio --exact` |
| JDK para `sdkmanager` | OpenJDK 25.0.3, el que trae Studio en `C:\Program Files\Android\Android Studio\jbr` | con Studio |
| JDK para Gradle | Temurin 17.0.20 en `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot` | `winget install --id EclipseAdoptium.Temurin.17.JDK --exact` |
| Command-line tools | `commandlinetools-win-15859902_latest.zip` (SHA256 verificado) | descarga manual a `Sdk\cmdline-tools\latest` |
| Platform-tools (`adb`) | ver `adb version` | `sdkmanager --install platform-tools` |
| Plataforma | `platforms;android-36` | `sdkmanager` |
| Build-tools | `build-tools;36.0.0` | `sdkmanager` |
| NDK | `ndk;29.0.13113456` | `sdkmanager` |
| CMake | `cmake;3.31.6` | `sdkmanager` |

Las versiones de plataforma, NDK y CMake son **las que fija el ejemplo oficial**
`examples/llama.android` de llama.cpp en el tag b10941 (`lib/build.gradle.kts`):
`compileSdk = 36`, `ndkVersion = "29.0.13113456"`, CMake `3.31.6`, `minSdk = 33`, AGP 8.13.2,
Kotlin 2.3.0, Gradle 8.14.3, JDK 17 como toolchain.

Variables de entorno de usuario:

```text
ANDROID_HOME = C:\Users\Francisco\AppData\Local\Android\Sdk
PATH += %ANDROID_HOME%\platform-tools;%ANDROID_HOME%\cmdline-tools\latest\bin
```

Para usar `sdkmanager` desde una terminal hace falta `JAVA_HOME` apuntando al JBR de Studio
(no se ha fijado globalmente para no interferir con el Java 8 del sistema):

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
sdkmanager --list_installed
```

## Cómo compila el ejemplo oficial (para no asumirlo de memoria)

Flags CMake que pasa `lib/build.gradle.kts` en b10941:

```text
-DBUILD_SHARED_LIBS=ON -DLLAMA_BUILD_COMMON=ON -DLLAMA_OPENSSL=OFF
-DGGML_NATIVE=OFF -DGGML_BACKEND_DL=ON -DGGML_CPU_ALL_VARIANTS=ON -DGGML_LLAMAFILE=OFF
```

Es decir: backend CPU cargado dinámicamente con **todas las variantes** de kernels ARM
compiladas, y se elige en tiempo de ejecución según el SoC. En `arm64-v8a` el `CMakeLists.txt`
del módulo activa además KleidiAI y OpenMP. ABIs del ejemplo: `arm64-v8a` y `x86_64`; para la
app propia se dejará solo `arm64-v8a`.

## Móvil (rellenar en el paso 1.7–1.10)

| Campo | Valor |
| --- | --- |
| Modelo | Samsung Galaxy A53 5G |
| Android / API | pendiente (`adb shell getprop ro.build.version.release` / `.sdk`) |
| `MemTotal` / `MemAvailable` en reposo | pendiente (`adb shell cat /proc/meminfo`) |
| Espacio libre en `/sdcard` | pendiente (`adb shell df -h /sdcard`) |
| `adb devices` | pendiente |

## Valores por defecto del motor en el ejemplo (`lib/src/main/cpp/ai_chat.cpp`, b10941)

| Constante | Valor | Consecuencia para el A53 |
| --- | --- | --- |
| `DEFAULT_CONTEXT_SIZE` | 8192 | Demasiado para 2 GB libres. La app propia lo baja a 2048 (plan 01 §2) |
| `N_THREADS_MIN` / `MAX` / `HEADROOM` | 2 / 4 / 2 | En 8 núcleos: min(4, 8−2) = 4 hilos. Coincide con el punto de partida del plan |
| `BATCH_SIZE` | 512 | OK |
| `DEFAULT_SAMPLER_TEMP` | 0.3 | La app propia usa 0.2 como el protocolo |

Como estas constantes no son configurables desde Kotlin, la app propia llevará **su propia copia
del módulo `lib`** con esos valores ajustados, en vez de referenciar el del submódulo.

La app del ejemplo elige el GGUF con el selector de ficheros del sistema y lo **copia** a su
almacenamiento interno (`filesDir/models/`). Para probarla basta con `adb push` del modelo a
`/sdcard/Download/` y seleccionarlo desde la app; la copia de 1,1 GB tarda un rato la primera vez.

## Toolchain validado (plan 01, paso 3.3, hecho fuera del repo)

El ejemplo oficial compila **sin modificar** desde este PC:

| Dato | Valor |
| --- | --- |
| Fuente | clon de llama.cpp en tag b10941, en `C:\tmp\zca-llama` (temporal, fuera del repo) |
| Comando | `JAVA_HOME=<Temurin 17> bash gradlew --no-daemon :app:assembleDebug` |
| Tiempo (primera vez, sin caché) | 4 min 11 s |
| Resultado | `app/build/outputs/apk/debug/app-debug.apk`, 109 MB (arm64-v8a + x86_64, todas las variantes de CPU) |

Queda pendiente instalarlo y medirlo en el A53 (pasos 3.4–3.6) cuando el móvil esté conectado.

## Incidencias

- `edgedl.me.gvt1.com` devolvió error HTTP para el zip de command-line tools; la misma ruta en
  `dl.google.com/android/repository/` funcionó a la primera.
- El clon de llama.cpp falla en Windows con "Filename too long" (rutas de `tools/ui`). Arreglo:
  `git config --global core.longpaths true`. Afecta también al submódulo del repo.
- El ejemplo solo trae `gradlew` (sin `.bat`): se lanza con `bash gradlew` desde Git Bash.
- Gradle 8.14.3 no arranca con el Java 25 de Android Studio ("What went wrong: 25.0.3"). Hace
  falta un JDK 17 aparte; con `JAVA_HOME` apuntando a Temurin 17 compila.
- Con el proyecto en una ruta larga (~150 caracteres) la configuración de CMake falla en la
  descarga de KleidiAI: `ninja: error: manifest 'build.ninja' still dirty after 100 tries`.
  Gradle no muestra ese mensaje; salió al ejecutar `cmake.exe` a mano con los mismos argumentos.
  En `C:\tmp\zca-llama` configura sin problema. **La ruta del repo
  (`C:\MIOS\IAlogia\proyectos\ZeroCloudAssist\third_party\llama.cpp`) es corta y no debería
  verse afectada, pero si el submódulo falla igual, la salida es un `subst` o mover el repo.**
- `local.properties` es un fichero de propiedades Java: `sdk.dir=C:\Users\...` con barras
  invertidas simples se interpreta como escapes y el build falla en segundos sin mensaje útil.
  Escribirlo con barras normales: `sdk.dir=C:/Users/Francisco/AppData/Local/Android/Sdk`.
