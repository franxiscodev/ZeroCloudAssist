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

## Móvil (pasos 1.7–1.10, leído el 2026-09-13)

| Campo | Valor |
| --- | --- |
| Modelo | Samsung Galaxy A53 5G, `SM-A536E` (`a53xnsxx`) |
| Build | `BP2A.250605.031.A3.A536EXXSNGZG3` |
| Android / API | **16 / 36** (el ejemplo exige ≥ 33: OK) |
| SoC | Exynos 1280 (`s5e8825`): 2× Cortex-A78 (`0xd41`) + 6× Cortex-A55 (`0xd05`), 8 núcleos |
| Extensiones CPU | `asimddp` (dotprod) y `fphp`/`asimdhp` (fp16) **sí**; `i8mm` y SVE **no** |
| `MemTotal` | 5 517 932 kB (5,5 GB) |
| `MemAvailable` con apps abiertas | 2 053 524 kB (2,05 GB) |
| `MemAvailable` **en reposo** (apps cerradas, 3 muestras cada 5 s) | 2 129 832 / 2 118 360 / 2 130 144 kB → **2,13 GB** |
| Swap (zram, "RAM Plus" de Samsung) | 8 GB, 7,2 GB libres |
| Espacio libre | 41 GB en `/storage/emulated` |
| Batería al leer | 94 %, sin cargar, 35,2 °C |
| Conexión ADB | **Wi-Fi** (depuración inalámbrica), PC `192.168.1.37`, móvil `192.168.1.39` |

Lectura para el plan:

- **Puerta de memoria (§2b): pasa.** 2,13 GB en reposo frente al umbral de 1,8 GB, unos
  330 MB de margen. Qwen 1.5B sigue siendo el candidato principal.
- La zram de 8 GB hace que Android comprima memoria anónima antes de matar procesos. Los pesos
  del modelo van por mmap (páginas de fichero, no van a zram): bajo presión se descartan y se
  releen del almacenamiento, lo que se vería como tok/s inestable, no como cierre.
- Sin `i8mm`, llama.cpp elegirá la variante de kernels ARMv8.2 con dotprod
  (`libggml-cpu-android_armv8.2_*`). Confirmarlo en el log al cargar.

### Conectar por depuración inalámbrica

Vincular una sola vez (el código caduca si se cierra el diálogo o se bloquea la pantalla):

```powershell
# Móvil: Opciones de desarrollador > Depuración inalámbrica > Vincular con código
adb pair <IP>:<puerto-de-vinculación> <código>
```

Después ADB se conecta solo por mDNS. Si no, el puerto de conexión se ve en la pantalla
principal de Depuración inalámbrica o con `adb mdns services`, y se usa `adb connect <IP>:<puerto>`.
El puerto de conexión cambia al reiniciar el móvil o cambiar de Wi-Fi.

Desde Git Bash, las rutas del móvil (`/sdcard/...`) se reescriben como rutas de Windows y
`adb push` escribe donde no debe. Usar PowerShell o anteponer `MSYS_NO_PATHCONV=1`.

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
- **Límite de 260 caracteres de Windows (MAX_PATH) en el build nativo.** KleidiAI se descarga
  dentro del directorio de build de CMake y uno de sus ficheros tiene una cola fija de 138
  caracteres (`_deps/kleidiai-src/kai/ukernels/matmul/.../kai_matmul_clamp_f16_..._asm.S`).
  Con la ruta del prefijo, ninja falla con `Filename longer than 260 characters` (en el scratchpad
  de la sesión apareció como `manifest 'build.ninja' still dirty after 100 tries`, que Gradle no
  muestra: salió al lanzar `cmake.exe` a mano).

  | Ubicación del directorio de build | Longitud total | Resultado |
  | --- | --- | --- |
  | `third_party/llama.cpp/examples/llama.android/lib/.cxx/...` (ejemplo dentro del submódulo) | 261 | **Falla** |
  | `C:/tmp/zca-llama/examples/llama.android/lib/.cxx/...` | 214 | Compila |
  | `android/lib/.cxx/...` (app propia, etapa 4) | 224 | Cabe, 36 de margen |

  Consecuencias:
  - El ejemplo oficial **no se puede compilar desde dentro del submódulo** en esta máquina. El
    paso 3.2 del plan (abrirlo en Android Studio desde `third_party/`) no vale tal cual; el
    ejemplo se compila desde un clon en `C:\tmp\zca-llama` y su APK es el que se instala en 3.4.
  - La app propia en `android/` no necesita ningún apaño, pero **si el repo se clona en una ruta
    más de 36 caracteres más larga, volverá a fallar**.
  - Arreglo de raíz, opcional y con permisos de administrador: el ninja del SDK (1.12.1) declara
    `longPathAware` en su manifiesto, así que basta con activar las rutas largas en Windows:
    `Set-ItemProperty HKLM:\SYSTEM\CurrentControlSet\Control\FileSystem LongPathsEnabled 1`
    y reiniciar. Hoy está a `0`.
- `local.properties` es un fichero de propiedades Java: `sdk.dir=C:\Users\...` con barras
  invertidas simples se interpreta como escapes y el build falla en segundos sin mensaje útil.
  Escribirlo con barras normales: `sdk.dir=C:/Users/Francisco/AppData/Local/Android/Sdk`.
