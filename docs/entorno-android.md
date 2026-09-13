# Entorno Android en Windows

Plan 01, etapa 1. Instalado el 2026-09-13. Todo por línea de comandos, sin pasar por el
asistente gráfico de Android Studio.

## Versiones instaladas en el PC

| Componente | Versión | Cómo se instaló |
| --- | --- | --- |
| Android Studio | 2026.1.4 (Quail 4), build AI-261.26222.65 | `winget install --id Google.AndroidStudio --exact` |
| JDK | OpenJDK 25.0.3, el que trae Studio en `C:\Program Files\Android\Android Studio\jbr` | con Studio |
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

## Incidencias

- `edgedl.me.gvt1.com` devolvió error HTTP para el zip de command-line tools; la misma ruta en
  `dl.google.com/android/repository/` funcionó a la primera.
