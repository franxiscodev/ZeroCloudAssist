# Plan 02 — ZeroCloudAssist: respuestas con el manual del ABB ACS355 (RAG)

Aprobado el 2026-09-14, tras un brainstorming pregunta a pregunta con Francisco. Parte del plan 01
cerrado (tag `v0.1.0-hola-mundo`, Gate G2 = GO).

> **Para quien lo ejecute:** seguir con `superpowers:executing-plans`, etapa a etapa, parando en
> cada checkpoint marcado **[Francisco]**. Leer también los informes
> `informes/2026-09-13-medicion-modelos-a53.md` y `informes/2026-09-14-hola-mundo-a53.md`
> (secciones "Decisión según §3", "Revisión de código" y "Gate G2").

---

## 1. Contexto

Hoy la app propia (`android/`) carga Qwen2.5-1.5B-Instruct Q4_K_M en el Samsung A53 con 6 hilos y
`n_ctx` 2048, y responde en español en streaming, pero **solo con lo que el modelo sabe**: en el
plan 01 las respuestas técnicas salieron genéricas (4/6 en la rúbrica fina), P3 no mandó cortar
tensión, y el "F0009" no se puede contestar bien sin el manual.

**Objetivo del plan 02:** que la app responda a preguntas en español con el contenido del manual
de usuario del ACS355 (en inglés) y muestre de qué página sale, dentro de un tiempo de respuesta
fijado de antemano, con la pantalla "instrumento de taller" y con CI.

El riesgo que decide es el mismo que en el G1: **el procesado del prompt (~35 tok/s)**. Cada token
de manual que entra en el prompt cuesta ~29 ms antes de la primera palabra. Todo el diseño se
organiza alrededor de ese presupuesto.

### Condiciones heredadas (G1 y G2, no se reabren sin un dato nuevo)

1. 100 % offline y gratis, sin API keys ni servicios en la nube.
2. Contexto de manual de **≤ 250–300 tokens por pregunta**; prompt de sistema procesado una vez y
   reutilizado desde la caché KV; streaming.
3. Avisos de seguridad **puestos por la app**, no por el modelo.
4. Tope de respuesta de **350–400 tokens**; Markdown básico bien pintado; la vista baja hasta la
   marca de "respuesta cortada".
5. **~1,4 GB de RAM para el modelo** (repack, sin mmap).
6. Plan B si con RAG el tiempo no vale: móvil con i8mm, validado con el mismo protocolo.
7. Estética "instrumento de taller" (oscuro cálido, mono para códigos, acento ámbar, sin
   burbujas) con mockup previo usando la skill `design`.

### Datos verificados el 2026-09-14 (en este PC y en el repo)

| Dato | Valor | Cómo se obtuvo |
| --- | --- | --- |
| Manual de usuario | `manuales/EN_ACS355_UM_E_A5.pdf`, **440 páginas**, 22 capítulos, ~131 000 palabras, texto extraíble (sin OCR) | `pypdf`: páginas, índice y palabras |
| Guía rápida | `EN_ACS355_QISG_C_A3.pdf`, 2 páginas A3, 5 300 palabras, repite contenido del manual | `pypdf` |
| Página del PDF = página impresa | Sí en 438 de 440 (las dos sin número son la contraportada) | Número impreso al principio del texto de cada página |
| Tokens de Qwen por palabra en el manual | **1,83** (capítulo *Fault tracing*: 4 855 palabras → 8 863 tokens) | `llama-tokenize` b10941 con el GGUF de Qwen |
| Chunks esperados | ~1 850 de ~130 tokens (≈ 240 000 tokens / 130) | Cálculo |
| Fallo 0009 | **MOT OVERTEMP**, tabla de fallos p. 362. "Armario caliente" encaja con DEV OVERTEMP (p. 360) | Búsqueda en el texto |
| Regla de seguridad clave | p. 18: cortar la alimentación, **esperar 5 minutos** a que se descarguen los condensadores, comprobar con multímetro | Texto de la p. 18 |
| llama.cpp b10941 ejecuta multilingual-e5 | Sí: `conversion/bert.py` trae `XLMRobertaModel` y el tokenizador SentencePiece Unigram; `llama.h` trae `llama_encode`, `llama_get_embeddings_seq`, `pooling_type`, `embeddings`; ejemplo de referencia `examples/embedding/embedding.cpp` | Lectura del submódulo |
| llama.cpp del PC | `C:\tools\llama.cpp`, build 10941 (mismo commit que el submódulo). Trae `llama-server.exe` y `llama-tokenize.exe`; **no** trae `llama-embedding.exe` | `llama-cli --version`, listado |
| SQLite del sistema Android | **Sin FTS5** y sin carga de extensiones | [issue sqldelight #1977](https://github.com/sqldelight/sqldelight/issues/1977) |
| SQLite empaquetado | `androidx.sqlite` 2.7.1 (09-09-2026), `BundledSQLiteDriver`, minSdk 23, `addExtension` desde 2.6.0. FTS5 incluido según [un artículo de terceros](https://wsoh.released.at/blog/bundledsqlitedriver/), **no** según las notas oficiales → se comprueba en E2 | [notas de versión](https://developer.android.com/jetpack/androidx/releases/sqlite) |
| Licencias | Qwen2.5-1.5B-Instruct: **Apache 2.0**. multilingual-e5-small: **MIT**, exige los prefijos `query:` y `passage:` (seguidos de un espacio), 384 dimensiones | Fichas oficiales en Hugging Face |
| Permisos de la app | El manifiesto **no pide `INTERNET`**: la app no puede usar la red | `AndroidManifest.xml` |
| Finales de línea | `.gitattributes` con `* text=auto eol=lf`; `gradlew` ya está en LF | `git ls-files --eol` |

---

## 2. Decisiones cerradas

| Tema | Decisión | Por qué |
| --- | --- | --- |
| Qué manual entra | **Manual de usuario entero** (440 p.). Guía rápida fuera | El coste es del PC y la base queda pequeña; la guía repite contenido y citar "p. 2" no ayuda |
| Búsqueda español → inglés | **Híbrida sin traducir**: FTS5 para lo literal (códigos, números de parámetro, siglas) + vectores `multilingual-e5-small` para lo coloquial, fusionados con RRF | Traducir con Qwen sumaría 3–4 s y ensuciaría la caché KV; solo FTS5 falla con lo coloquial |
| Motor de embeddings en el móvil | **El mismo llama.cpp** (JNI nuevo en `lib`), e5 en GGUF Q8_0 cargado con mmap | Sin ONNX Runtime ni otro motor; mismo GGUF en PC y móvil → vectores comparables |
| Origen del GGUF de e5 | **Convertido por nosotros** desde `intfloat/multilingual-e5-small` con el conversor del submódulo | Sin binarios de terceros, como en el plan 01 |
| Memoria de conversación | **Preguntas independientes**: antes de cada pregunta la caché KV vuelve al prompt de sistema | TTFT constante (~10 s), generación estable, citas inequívocas; el contexto nunca se llena |
| Troceado | **~130 tokens de Qwen por chunk, cortando por la estructura** (una entrada de fallo o de parámetro por chunk, sin partir filas, sin cruzar página); entran los **2 mejores** | Con 1,83 tok/palabra, los 150–200 palabras del plan 01 §7 serían 275–365 tokens: uno solo llenaría el presupuesto |
| Almacén | **Un `.sqlite`** con FTS5 y los vectores en BLOB float32; coseno recorriendo los ~1 850 vectores en Kotlin (< 5 ms) | sqlite-vec (0.1.x) no aporta velocidad a este tamaño y añade un `.so` y carga de extensiones. Se reconsidera con decenas de miles de chunks |
| SQLite en Android | `androidx.sqlite:sqlite-bundled` (`BundledSQLiteDriver`). Alternativa: `requery/sqlite-android` | El SQLite del sistema no trae FTS5 |
| Citas | **Las pone la app** con las páginas de los chunks usados ("Fuentes: p. 362 · p. 360"); al tocar una se despliega el fragmento en inglés. Al modelo se le pide que **no** escriba páginas | Un 1.5B se inventa números de página; el fragmento prueba de dónde sale la respuesta sin llevar el PDF al móvil |
| Avisos de seguridad | **Reglas en la app** (disparadores en español e inglés sobre la pregunta y los chunks) → tarjeta ámbar **antes** de la respuesta con el aviso de la p. 18 y su cita | Determinista y probado con JUnit; no depende del modelo |
| Tope de respuesta | **400 tokens** | Condición del G2; 200 cortaba las respuestas con pasos |
| Repack | **Se mantiene** (~1,4 GB en RAM anónima). Solo se prueba sin repack si Android llega a matar la app | Acelera justo el procesado del prompt, que es el cuello de botella |
| Markdown | **Renderizador mínimo propio** (negrita, listas, títulos), función pura con tests | Una librería podría exigir otro BOM de Compose, que está fijado en 2026.06.01 |
| Despliegue | **`adb push` con script** (`tools/cargar-movil.ps1`) + tabla `meta` en el `.sqlite` + `docs/licencias.md`. Botón Importar, MDM y descarga desde servidor, **fuera** | MVP con un solo móvil; `meta` deja listo "actualizar el manual = cambiar el `.sqlite`" |
| CI | GitHub Actions en Ubuntu: job `android-unit` (tests JVM de `app`) y job `tools` (pytest), en cada PR y en `main`; checks obligatorios en la protección de `main` | Política global: CI que verifique de verdad |
| UI | Mockup con `design` de la pantalla de chat en **4 estados**, solo tema oscuro, aprobado antes de Compose | Los estados de espera y error son donde la demo puede quedar mal |
| Protocolo | §5 del plan 01 **no se toca**. Batería nueva en `docs/bateria-manual.yaml`; prompt de sistema nuevo en §6 de este plan y en `PromptBuilder.kt` | El protocolo del plan 01 sigue sirviendo de referencia sin manual |
| Git | Tres ramas y tres PR: `ci/github-actions` (E0), `feat/indice-manual` (E1), `feat/rag-app` (E2–E5). **Push y merge de Francisco** | Diffs revisables por separado |

### Restricciones globales (valen para todas las tareas)

- Sin red en tiempo de ejecución, sin API keys, sin secretos. No se añade el permiso `INTERNET`.
- Gradle con **JDK 17**; `gradlew` lanzado con `bash` desde `android/`.
- **Compose BOM 2026.06.01, AGP 8.13.2, `compileSdk` 36.** Antes de añadir una dependencia, mirar
  qué AGP y SDK exige (POM / metadatos de Gradle o `context7`). Si exige subir, se para y se decide.
- Cambios en `lib` con comentario `ZeroCloudAssist:`; paquetes `com.arm.aichat.*` intactos (JNI).
- Python en `tools/` con **uv**; nada pesado al repo (`models/`, `*.sqlite`, PDFs ya ignorados).
- `adb push` / `adb shell` con rutas `/sdcard/...` desde **PowerShell** (Git Bash las reescribe).
- Nunca `uiautomator dump` durante una generación o una medición.
- Tras cada `adb install -r`: `adb shell pm uninstall --user 95 com.ialogia.zerocloudassist`.

---

## 2b. Presupuesto de tokens, tiempo y memoria

### Por pregunta (`n_ctx` 2048)

| Parte | Tokens | Se procesa en cada pregunta |
| --- | --- | --- |
| Prompt de sistema (§6) | 90 medidos con `llama-tokenize` el 2026-09-14, sin la plantilla de chat (tope 100 con ella; se remide en 3.5) | No: queda en la caché KV |
| Fragmentos del manual (2 chunks + cabeceras) | ≤ 300 | Sí |
| Pregunta + plantilla del turno | ~40 | Sí |
| Respuesta | ≤ 400 | — (generación) |
| **Total en la caché** | **≤ ~830 de 2048** | Nunca hace falta el desplazamiento de contexto |

### Tiempo hasta la primera palabra (previsión)

~340 tokens a 35 tok/s ≈ **9,7 s** + vectorizar la pregunta y buscar (< 0,3 s) ≈ **~10 s**. Las
fuentes y la tarjeta de seguridad se pintan **antes** de procesar el prompt (< 1 s desde que se
pulsa), para que el técnico tenga dónde mirar mientras espera.

### Memoria

| Componente | Estimado | Tipo |
| --- | --- | --- |
| Qwen 1.5B con repack + KV + buffers | ~1,4 GB | Anónima (medido en el plan 01) |
| e5-small Q8_0 | ~130 MB de fichero; residente solo lo tocado | mmap (verificar en 2.3 que no se reempaqueta) |
| Vectores en memoria | ~2,8 MB (1 850 × 384 × 4) | Heap de Java |
| Texto de los chunks | ~1–2 MB | SQLite / heap |
| **Total** | **~1,45–1,55 GB** sobre ~2 GB disponibles | |

---

## 3. Criterios go/no-go (fijados antes de medir)

### Gate G3 — recuperación, en el PC (fin de la etapa 1)

Batería de 12 preguntas (§6); la de "fuera del manual" no cuenta aquí → 11. Acierto = alguna de
las `paginas_esperadas` está entre las páginas de los chunks que entrarían en el prompt (top-2
tras el presupuesto).

| Métrica (búsqueda híbrida) | GO | GO con reservas | NO-GO |
| --- | --- | --- | --- |
| Página esperada en el top-2 | ≥ 9/11 | 7–8 | ≤ 6 |

FTS5 solo y vectores solos se miden y se anotan, pero no deciden.

- **Con reservas:** **una** ronda de ajuste (troceado o normalización de términos) y se vuelve a
  medir. Si sigue con reservas, decide Francisco.
- **NO-GO:** se replantea la búsqueda (traducir la consulta o glosario). Decide Francisco, y queda
  en el informe.

### Gate G4 — demo, en el A53 (fin de la etapa 5)

Una ejecución por pregunta (con temperatura 0,2 y la caché reiniciada, repetir da el mismo texto,
como se vio en el plan 01). **TTFT = desde que se pulsa "Preguntar" hasta el primer token**, con la
vectorización, la búsqueda y el procesado del prompt dentro.

| Criterio | GO | GO con reservas | NO-GO |
| --- | --- | --- | --- |
| Tiempo hasta la primera palabra (mediana de las 12) | ≤ 10 s | 10–15 s | > 15 s |
| Calidad (rúbrica §6, 0–2 × 12) | ≥ 18/24 | 14–17 | ≤ 13 |
| Generación (mediana) | ≥ 8 tok/s | 5–8 | < 5 |
| La pregunta fuera del manual no inventa | Sí | — | No |
| Tarjeta de seguridad en las preguntas con `riesgo: true` | 2/2 | — | < 2 |
| Minimizar 60 s y volver; sin cierres durante la batería ni en la tanda de estrés (5.3) | Sí | — | No |

Reglas de decisión:

1. **TTFT con reservas:** antes de la demo, el presupuesto de manual baja a ~200 tokens (1 chunk
   largo o 2 cortos) y se remide.
2. **TTFT NO-GO:** plan B, móvil con i8mm, validado con esta misma batería.
3. **Calidad NO-GO con G3 en GO:** el fallo es del modelo, no de la búsqueda. **Una** ronda de
   ajuste del prompt de sistema (cambiarlo en los sitios de §6) y se remide; si no basta,
   replanteo. Decide Francisco.
4. Cualquier NO-GO en los criterios Sí/No (inventar, seguridad, cierres) bloquea la demo hasta
   corregirlo; no hay "con reservas".
5. Las decisiones G3 y G4 son de Francisco: Claude recomienda, no decide.

---

## 4. Mapa de ficheros

```text
.github/workflows/ci.yml                     E0  jobs android-unit y tools
docs/bateria-manual.yaml                     E1  12 preguntas con página esperada
docs/licencias.md                            E3  Qwen, e5, llama.cpp, texto del manual de ABB
docs/modelos.md                              E1  + SHA256 de e5 Q8_0 y del tokenizador de Qwen
docs/diseno-ui.md                            E4  tokens de color y tipografía del mockup aprobado
tools/pyproject.toml                         E1  proyecto uv "zca-tools"
tools/zca_tools/extraer.py                   E1  PDF → páginas limpias con capítulo
tools/zca_tools/trocear.py                   E1  páginas → chunks por estructura
tools/zca_tools/terminos.py                  E1  términos literales y consulta FTS5 (gemelo de QueryTerms.kt)
tools/zca_tools/tokens.py                    E1  contador con el tokenizador de Qwen
tools/zca_tools/vectorizar.py                E1  cliente de llama-server --embedding
tools/zca_tools/construir.py                 E1  escribe el .sqlite (esquema v1)
tools/zca_tools/evaluar.py                   E1  RRF, presupuesto y recall@2 (gemelo de ManualSearch.kt)
tools/zca_tools/cli.py                       E1  `uv run zca-indice …`
tools/tests/…                                E1  pytest
tools/cargar-movil.ps1                       E3  adb push de los 3 ficheros + SHA256
tools/estres-movil.ps1                       E3  20 preguntas seguidas por ADB sin reiniciar la app (guardia de estrés)
android/lib/src/main/cpp/embedder.cpp        E2  JNI de embeddings (ZeroCloudAssist:)
android/lib/src/main/cpp/ai_chat.cpp         E2  + resetToSystemPrompt (ZeroCloudAssist:)
android/lib/src/main/cpp/CMakeLists.txt      E2  + embedder.cpp
android/lib/src/main/java/com/arm/aichat/Embedder.kt              E2
android/lib/src/main/java/com/arm/aichat/InferenceEngine.kt       E2  + resetConversation()
android/lib/src/main/java/com/arm/aichat/internal/InferenceEngineImpl.kt  E2
android/app/src/main/java/com/ialogia/zerocloudassist/
  rag/QueryTerms.kt  rag/ManualSearch.kt  rag/ManualStore.kt  rag/MetaCheck.kt    E3
  rag/PromptBuilder.kt  rag/SafetyRules.kt  rag/SourceList.kt                    E3
  ui/MarkdownLite.kt                                                              E3
  Assistant.kt  Conversation.kt  Metrics.kt  ModelLocation.kt                    E3 (modificar)
  ui/Theme.kt  ui/ChatScreen.kt  ui/MarkdownText.kt  ui/SourcesRow.kt  ui/SafetyCard.kt  E4
  MainActivity.kt                                                                 E4 (queda en ciclo de vida)
android/app/src/test/java/com/ialogia/zerocloudassist/…Test.kt    E3  un test por fichero de lógica
```

Artefactos (ignorados por git, en `models/`): `multilingual-e5-small-q8_0.gguf`, `acs355.sqlite`,
`qwen2.5-tokenizer.json`, `bateria-vectores.json`.

**Reglas duplicadas en Python y Kotlin** (términos literales, RRF, presupuesto): cada lado tiene
su test con **la misma tabla de casos** de este plan. Si cambia una regla, cambia en los dos
lados y en la tabla.

---

## 5. Etapas, pasos y verificación

Estimación: E0 medio día · E1 1,5–2 días · E2 1 día · E3 1,5–2 días · E4 1,5–2 días · E5 1 día.

**En cada etapa**, el informe correspondiente lleva una sección **"Incidencias"** que se escribe
**en el momento** en que pasa algo (qué se supuso, qué falló, cómo se detectó, cuánto costó). Es
la materia prima del informe de lecciones de 5.10 y de las charlas de Francisco sobre cómo se
desarrolló la app con Claude Code.

### Etapa 0 — CI en GitHub Actions (medio día)

**Rama:** `ci/github-actions` desde `main` actualizado (`Start-Service ssh-agent`,
`git fetch --prune`, `main == origin/main`).

| # | Paso | Detalle | Verificación |
| --- | --- | --- | --- |
| 0.1 | Rama | `git switch -c ci/github-actions` | `git status` limpio |
| 0.2 | Tests visibles en el log | En `android/app/build.gradle.kts`: `tasks.withType<Test>().configureEach { testLogging { events("passed", "skipped", "failed") } }` | En local, `gradlew :app:testDebugUnitTest` imprime cada test con `PASSED` |
| 0.3 | Workflow | `.github/workflows/ci.yml` (abajo). Versión mayor vigente de cada acción, comprobada con `gh api repos/<owner>/<acción>/releases/latest` | — |
| 0.4 | **Reproducir en Linux antes del push** | Desde PowerShell: `docker run --rm -v "${PWD}:/repo" -w /repo/android <imagen con JDK 17 + Android SDK 36> bash gradlew :app:testDebugUnitTest`. Riesgo concreto: que configurar `lib` exija NDK/CMake o el submódulo. Si lo exige: `submodules: recursive` + NDK 29.0.13113456 en el workflow, o separar la configuración nativa (decidir con el dato) | `BUILD SUCCESSFUL` y los mismos tests `PASSED` que en Windows (Conversation, Metrics, ModelLocation) |
| 0.5 | Commit y PR | `ci: tests unitarios de la app en GitHub Actions`. `finishing-a-development-branch`: resumen + comando de push para Francisco; `gh pr create` | PR abierto |
| 0.6 | CI en el PR | `gh run watch`; `gh run view --log` | El log muestra cada test `PASSED` (no solo el job en verde) |
| 0.7 | Check obligatorio | Leer la protección actual (`gh api repos/franxiscodev/ZeroCloudAssist/branches/main/protection`) y volver a hacer `PUT` **conservando** PR obligatorio, `enforce_admins` y sin force-push, añadiendo `required_status_checks: {strict: true, contexts: ["android-unit"]}`. Sin filtros `paths` en el workflow: un check obligatorio que no corre bloquea los PR de solo docs | `gh api …/protection --jq .required_status_checks.contexts` → `["android-unit"]` |

```yaml
# .github/workflows/ci.yml (E0; el job tools se añade en 1.13)
name: CI
on:
  pull_request:
  push:
    branches: [main]
jobs:
  android-unit:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4          # fijar la versión vigente en 0.3
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: "17" }
      - uses: gradle/actions/setup-gradle@v4
      - run: bash gradlew :app:testDebugUnitTest
        working-directory: android
```

### Etapa 1 — Índice del manual en el PC (1,5–2 días)

**Rama:** `feat/indice-manual` desde `main` con E0 fusionada.

| # | Paso | Detalle | Verificación |
| --- | --- | --- | --- |
| 1.1 | Proyecto uv | `tools/pyproject.toml`: `name = "zca-tools"`, `requires-python = ">=3.11"`, deps `pypdf`, `numpy`, `pyyaml`, `tokenizers`, `huggingface_hub`, `requests`; dev `pytest`; script `zca-indice = "zca_tools.cli:main"`. `tools/calidad-pc.sh` se queda como está | `uv run pytest` desde `tools/` corre (aunque sea con 0 tests) |
| 1.2 | e5 a GGUF | Descargar `intfloat/multilingual-e5-small` con `huggingface_hub.snapshot_download(revision=<commit fijado>)` a `models/hf/`. Convertir: `uv run --with-requirements third_party/llama.cpp/requirements/requirements-convert_hf_to_gguf.txt python third_party/llama.cpp/convert_hf_to_gguf.py models/hf/multilingual-e5-small --outtype q8_0 --outfile models/multilingual-e5-small-q8_0.gguf`. Descargar también `tokenizer.json` de `Qwen/Qwen2.5-1.5B-Instruct` → `models/qwen2.5-tokenizer.json`. SHA256 y commit de origen de ambos en `docs/modelos.md` | `llama-server -m models/multilingual-e5-small-q8_0.gguf --embedding --pooling mean --port 8090` arranca. Prueba de cordura: sim(`query: el motor se calienta demasiado`, `passage: Motor overtemperature`) > sim(misma pregunta, `passage: Fieldbus communication settings`) |
| 1.3 | Extraer (**TDD**) | `extraer.py` (interfaz abajo). Tests con cadenas: quita la cabecera `"358   Fault tracing"` y el pie; conserva las líneas de tabla. Test de integración marcado `@pytest.mark.manual` (se salta si falta el PDF): 440 páginas, capítulo de la p. 362 = "Fault tracing" | pytest verde |
| 1.4 | Contador de tokens (**TDD**) | `tokens.py`: `contador_qwen(ruta)`. Test `manual`: las páginas 351–370 dan 8 863 ± 2 % (el dato de `llama-tokenize`) | pytest verde |
| 1.5 | Trocear (**TDD**) | `trocear.py` con los casos de la tabla de abajo, sobre textos **sintéticos** que imitan la estructura del manual (no se copian fragmentos de ABB a los tests) | pytest verde |
| 1.6 | Términos literales (**TDD**) | `terminos.py` con la tabla de casos de abajo | pytest verde |
| 1.7 | Vectorizar | `vectorizar.py`: cliente HTTP de `llama-server` en `127.0.0.1:8090`; **endpoint y forma de la respuesta según `tools/server/README.md` del tag b10941**, no de memoria. Normaliza L2 siempre en Python. Test TDD de `normalizar()`; test `manual` contra el servidor | Vector de 384, norma 1 ± 1e-5 |
| 1.8 | Construir el `.sqlite` (**TDD**) | `construir.py`, esquema v1 (abajo). Test con 3 chunks falsos en `tmp_path`: `MATCH '"0009"'` devuelve el chunk correcto; el BLOB vuelve a dar los mismos 384 floats; `meta` tiene todas las claves | pytest verde |
| 1.9 | Generar el índice | `uv run zca-indice construir --pdf manuales/EN_ACS355_UM_E_A5.pdf --tokenizador models/qwen2.5-tokenizer.json --e5 http://127.0.0.1:8090 --salida models/acs355.sqlite` | Informe en consola: 1 500–2 200 chunks, tokens medios ~130, **máximo ≤ 170**. Claude lee 10 chunks al azar y la entrada 0009 (p. 362) queda en trozos que empiezan todos por "0009 MOT OVERTEMP" (mide 266 tokens: no cabe en uno; decidido con Francisco el 2026-09-14, ver `informes/2026-09-14-indice-manual.md`) |
| 1.10 | Batería **[Francisco]** | Claude redacta `docs/bateria-manual.yaml` (formato y reparto en §6), comprobando cada página esperada en el texto del PDF. **Francisco la revisa y la aprueba antes de medir.** Se commitea solo aprobada | Aprobación escrita en el informe |
| 1.11 | Evaluación (**TDD** en la lógica) | `evaluar.py`: `rrf()`, `seleccionar()` (presupuesto) y `acierta()` con las tablas de abajo. Después, `uv run zca-indice evaluar --bateria docs/bateria-manual.yaml --indice models/acs355.sqlite --e5 …` → tabla por pregunta (páginas devueltas por FTS5, vectores e híbrida) | Informe `informes/AAAA-MM-DD-indice-manual.md` con la tabla y el recall@2 de los tres métodos |
| 1.12 | Vectores de la batería | `uv run zca-indice vectores-bateria … --salida models/bateria-vectores.json` (`{id, pregunta, vector}`), para comparar con el móvil en 2.3 | Fichero con 12 vectores |
| 1.13 | CI de `tools` | Job `tools` en `ci.yml`: `astral-sh/setup-uv` (versión vigente), `uv run pytest -m "not manual"` en `tools/`. Añadir `"tools"` a los checks obligatorios como en 0.7 | El log del runner lista los tests `PASSED`; `contexts` = `["android-unit","tools"]` |
| 1.14 | **Gate G3 [Francisco]** | Aplicar §3 y escribir la recomendación en el informe | Decisión de Francisco escrita |
| 1.15 | Commits y PR | `build:` / `feat:` / `test:` / `docs:` atómicos; `finishing-a-development-branch` | PR abierto; Francisco fusiona |

**Interfaces de `tools/` (Python):**

```python
@dataclass(frozen=True)
class Pagina:
    numero: int        # página impresa = página del PDF
    capitulo: str      # del índice del PDF (outline)
    texto: str         # sin cabecera ni pie

@dataclass(frozen=True)
class Chunk:
    pagina: int
    capitulo: str
    texto: str
    tokens: int        # tokens de Qwen

def leer_manual(ruta: Path) -> list[Pagina]: ...
def limpiar_cabecera(texto: str, numero: int, capitulo: str) -> str: ...
def contador_qwen(ruta_tokenizer: Path) -> Callable[[str], int]: ...
def trocear(paginas: list[Pagina], contar: Callable[[str], int],
            objetivo: int = 130, maximo: int = 170) -> list[Chunk]: ...
def terminos_literales(pregunta: str) -> list[str]: ...
def consulta_fts(pregunta: str) -> str | None: ...
def normalizar(v: np.ndarray) -> np.ndarray: ...
def vectorizar(textos: list[str], url: str) -> np.ndarray: ...          # (n, 384) float32, L2 = 1
def construir(ruta: Path, chunks: list[Chunk], vectores: np.ndarray, meta: dict[str, str]) -> None: ...
def rrf(rankings: list[list[int]], k: int = 60) -> list[int]: ...
def seleccionar(ids: list[int], tokens: dict[int, int],
                max_tokens: int = 300, max_chunks: int = 2, cabecera: int = 8) -> list[int]: ...
def acierta(paginas_devueltas: list[int], paginas_esperadas: list[int]) -> bool: ...
```

**Casos de `trocear` (tests con contador `len(texto.split())`):**

| Caso | Entrada | Esperado |
| --- | --- | --- |
| Entrada de fallo | Tabla con 3 entradas "`NNNN NOMBRE` + causa + qué hacer" de ~40 palabras | 1–3 chunks; **ninguna entrada partida** |
| Trampa de la continuación | Una línea que **empieza por un número de parámetro pero continúa la frase anterior** ("…defined by parameter\n1202 CONST SPEED 1 or speed…", como en la p. 200) | No abre bloque nuevo: solo abre bloque `^\d{4} [A-Z]` si la línea anterior termina en fin de frase o es una cabecera de tabla |
| Párrafos cortos | Dos párrafos de 60 | Un chunk de 120 |
| Bloque largo | Un párrafo de 400 | Partido por frases; cada trozo ≤ `maximo` |
| Frontera de página | Texto al final de la p. 10 y al principio de la 11 | Nunca un chunk con dos páginas |
| Vacío | Página solo con cabecera | Sin chunks |

**Casos de términos literales (idénticos en `terminos.py` y `QueryTerms.kt`):**

| Pregunta | `terminos_literales` | `consulta_fts` |
| --- | --- | --- |
| `El variador muestra el fallo F0009` | `["0009"]` | `"0009"` |
| `¿qué es la alarma A2001?` | `["2001"]` | `"2001"` |
| `¿Cómo configuro el STO?` | `["STO"]` | `"STO"` |
| `la entrada DI1 no responde` | `["DI1"]` | `"DI1"` |
| `¿Para qué sirve el parámetro 9905?` | `["9905"]` | `"9905"` |
| `fallo f0009 y STO` | `["0009", "STO"]` | `"0009" OR "STO"` |
| `el motor se calienta mucho` | `[]` | `None` |
| `Tarda 5 minutos` | `[]` | `None` |

Regla: `[FfAa]?\d{4}` → los 4 dígitos; siglas de 2–4 mayúsculas con hasta 2 dígitos
(`[A-Z]{2,4}\d{0,2}` o `[A-Z]{1,3}\d{1,2}`) tal como aparecen en la pregunta; sin duplicados, en
orden de aparición; comillas dobles alrededor de cada término en la consulta FTS5.

**Casos de RRF y presupuesto (idénticos en `evaluar.py` y `ManualSearch.kt`):**

| Función | Entrada | Esperado |
| --- | --- | --- |
| `rrf` | `[[1, 2, 3], [3, 1]]`, k = 60 | `[1, 3, 2]` (1: 1/61 + 1/62; 3: 1/63 + 1/61; 2: 1/62) |
| `rrf` | `[[], [5, 4]]` | `[5, 4]` |
| `seleccionar` | ids `[1, 2, 3]`, tokens `{1: 130, 2: 140, 3: 120}` | `[1, 2]` (130 + 8 + 140 + 8 = 286 ≤ 300) |
| `seleccionar` | ids `[1, 2, 3]`, tokens `{1: 200, 2: 150, 3: 80}` | `[1, 3]` (el 2 no cabe y se salta; se sigue en orden) |
| `seleccionar` | ids `[1]`, tokens `{1: 320}` | `[]` (ningún chunk debe superar `maximo` = 170; si pasa, es un fallo de `trocear`) |

**Esquema del `.sqlite` (v1):**

```sql
CREATE TABLE meta (clave TEXT PRIMARY KEY, valor TEXT NOT NULL);
-- claves obligatorias: esquema='1', manual='ABB ACS355 User''s manual',
--   documento='EN_ACS355_UM_E_A5.pdf', version='AAAA-MM-DD.N',
--   embeddings='multilingual-e5-small-q8_0', embeddings_sha256='<sha>', dimension='384',
--   tokens_objetivo='130'
CREATE TABLE chunks (
  id       INTEGER PRIMARY KEY,
  pagina   INTEGER NOT NULL,
  capitulo TEXT    NOT NULL,
  texto    TEXT    NOT NULL,
  tokens   INTEGER NOT NULL,
  vector   BLOB    NOT NULL          -- 384 float32 little-endian, norma L2 = 1, de "passage: " + texto
);
CREATE VIRTUAL TABLE chunks_fts USING fts5(
  texto, content='chunks', content_rowid='id', tokenize='unicode61'
);
-- Añadido en E1 tras G3 (2026-09-14): glosario taller → manual, listas separadas por "|"
CREATE TABLE glosario (es TEXT NOT NULL, en TEXT NOT NULL);
-- meta gana capitulo_codigos='Fault tracing' y capitulo_parametros='Actual signals and parameters'
```

**Búsqueda que sale de G3** (GO con reservas acotado al MVP; detalle y pendientes de producción en
`informes/2026-09-14-indice-manual.md`): la consulta FTS5 = términos literales + expansiones del
glosario (`docs/glosario-taller.yaml`, viaja en la tabla `glosario`); los resultados de FTS5 se
piden **todos** por `rank`, se reordenan con `priorizar` (primero los chunks con una línea que
empieza por el código y, si la pregunta es de fallo/alarma o de parámetro, los de su capítulo) y se
recortan a 10; después RRF con los vectores y `seleccionar`. Las tablas de casos gemelas están en
`tools/tests/test_terminos.py` y `tools/tests/test_evaluar.py`. La traducción de la pregunta con
Qwen se midió y se descartó.

### Etapa 2 — Piezas nativas en el A53 (1 día)

**Rama:** `feat/rag-app` desde `main` con E1 fusionada y G3 en GO (o con reservas aceptadas).
Cada paso se prueba en el móvil antes de montar nada encima. Si algo falla:
`systematic-debugging`, no parchear a ciegas.

| # | Paso | Detalle | Verificación |
| --- | --- | --- | --- |
| 2.1 | Rama | `git switch -c feat/rag-app` | — |
| 2.2 | SQLite empaquetado | Comprobar qué AGP/SDK exige `androidx.sqlite:sqlite-bundled` 2.7.1 (y si no encaja, la última que encaje). Añadirla. Copiar `models/acs355.sqlite` con `tools/cargar-movil.ps1` (paso 3.13, adelantado aquí en su forma mínima). Al arrancar (solo en debug), abrir `files/manuales/acs355.sqlite` **en solo lectura** y ejecutar `SELECT c.id, c.pagina FROM chunks_fts f JOIN chunks c ON c.id = f.rowid WHERE chunks_fts MATCH '"0009"' ORDER BY rank`; log `ZCA` | logcat muestra la p. 362 y las mismas filas, en el mismo orden, que la consulta en el PC (con `LIMIT 3` y sin orden salían las tres primeras por id: incidencia 1 de E2). Si falla con "no such module: fts5": `requery/sqlite-android` y anotarlo en Incidencias |
| 2.3 | Embedder JNI | `embedder.cpp` en el mismo `.so` (`add_library(... ai_chat.cpp embedder.cpp)`), con **sus propias** variables globales. Siguiendo `examples/embedding/embedding.cpp` de b10941: modelo con `use_mmap = true`; contexto con `embeddings = true`, `pooling_type = LLAMA_POOLING_TYPE_MEAN`, `n_ctx = n_batch = n_ubatch = 512`, 6 hilos; tokenizar con tokens especiales; `llama_encode` (e5 es solo codificador); `llama_get_embeddings_seq(ctx, 0)`; normalizar L2. Se carga **después** de que el motor esté en `Initialized` (los backends de ggml ya cargados). Si el log muestra `CPU_REPACK` para e5, desactivar el reempaquetado en ese modelo (campo de `llama_model_params` según `llama.h` b10941) para conservar el mmap | Pantalla de depuración o log: para las 12 preguntas de `bateria-vectores.json`, **coseno móvil–PC ≥ 0,99** en todas y **< 0,3 s** por pregunta |
| 2.4 | Reiniciar a prompt de sistema | En `ai_chat.cpp`: `llama_memory_seq_rm(mem, 0, system_prompt_position, -1); current_position = system_prompt_position;`. En Kotlin, `resetConversation()` (interfaz abajo), válido solo en `ModelReady` | Dos preguntas seguidas: el log de la segunda empieza a procesar en `system_prompt_position` (no a continuación de la primera) |
| 2.5 | Memoria | Con Qwen + e5 + base cargados: `dumpsys meminfo com.ialogia.zerocloudassist`, `MemAvailable`; minimizar 60 s y volver (e5 también se libera en `onStop`) | Anotado en Incidencias; sin cierres. Si Android mata la app: primero liberar e5 entre preguntas; después, probar sin repack |
| 2.6 | Commits | `feat(lib): embeddings con e5 por JNI`, `feat(lib): reiniciar la conversación al prompt de sistema`, `build: sqlite empaquetado con FTS5` | — |

**Interfaces nuevas en `lib` (Kotlin):**

```kotlin
// com.arm.aichat.InferenceEngine — ZeroCloudAssist:
/** Vuelve la caché KV al final del prompt de sistema. Solo en State.ModelReady; si no, IllegalStateException. */
suspend fun resetConversation()

// com.arm.aichat.Embedder — ZeroCloudAssist: (JNI en embedder.cpp)
object Embedder {
    /** Requiere que el InferenceEngine esté en Initialized. Devuelve false si no pudo cargar. */
    suspend fun load(pathToModel: String): Boolean
    /** Vector L2-normalizado de 384. El llamador antepone "query: ". */
    suspend fun embed(text: String): FloatArray
    fun unload()
    val isLoaded: Boolean
}
```

### Etapa 3 — Lógica del RAG en la app (1,5–2 días)

**Misma rama.** Todo lo que es lógica pura va con **TDD** (test primero, verlo fallar, código
mínimo, verde, commit). Tests con `JAVA_HOME=<Temurin 17> bash gradlew :app:testDebugUnitTest`.

| # | Paso | Fichero y tests | Verificación |
| --- | --- | --- | --- |
| 3.1 | Términos literales (**TDD**) | `rag/QueryTerms.kt`, tabla de casos de E1, más el glosario (`expansions`, comparación sin tildes, palabra entera o prefijo con `*`) y `literalClass` (fallo/alarma frente a parámetro), con los casos de `tools/tests/test_terminos.py` | Verde |
| 3.2 | Búsqueda pura (**TDD**) | `rag/ManualSearch.kt`: `cosineTopK`, `rrf`, `select` y `prioritize` (gemelo de `priorizar`), con las tablas de E1 y de `tools/tests/test_evaluar.py` más: `cosineTopK` con 3 vectores de dimensión 2 devuelve el orden correcto | Verde |
| 3.3 | Almacén | `rag/ManualStore.kt`: abre el `.sqlite` en solo lectura, lee `meta`, carga chunks, vectores (BLOB little-endian → `FloatArray` plano) y la tabla `glosario`, `ftsIds(query)` devuelve todos por `rank` (se priorizan y recortan a 10 en `ManualSearch`). Sin tests JVM (SQLite nativo); se verifica en el móvil en 3.14 | — |
| 3.4 | Comprobación de `meta` (**TDD**) | `rag/MetaCheck.kt`: `null` → `Missing` con el `adb push`; `esquema = "2"` → `Incompatible`; `embeddings` distinto del e5 esperado → `Incompatible`; correcto → `Ok(ManualMeta(manual, version))` | Verde |
| 3.5 | Prompt (**TDD**) | `rag/PromptBuilder.kt`: `SYSTEM_PROMPT` (§6) y `userTurn(question, chunks)` con el formato exacto de §6. Tests: cadena exacta con 2 chunks; con 0 chunks lleva "(sin fragmentos)"; **nunca contiene "p. "**. Medir los tokens del prompt de sistema con `llama-tokenize` en el PC: ≤ 100 | Verde |
| 3.6 | Seguridad (**TDD**) | `rag/SafetyRules.kt` con los casos de abajo | Verde |
| 3.7 | Fuentes (**TDD**) | `rag/SourceList.kt`: una fuente por página, en el orden de los chunks; conserva capítulo y texto para desplegar | Verde |
| 3.8 | Markdown (**TDD**) | `ui/MarkdownLite.kt` con los casos de abajo | Verde |
| 3.9 | Conversación (**TDD**) | `Conversation.kt`: `Turn` gana `sources: List<Source> = emptyList()` y `safety: SafetyNotice? = null`; `attach(entries, sources, safety)` (no `withContext`: chocaría con `kotlinx.coroutines.withContext`, que `Assistant` ya usa); `INDEPENDENT_NOTICE` = "Cada pregunta se responde por separado con el manual: el asistente no recuerda las anteriores." (una vez, al principio) | Tests existentes + nuevos verdes |
| 3.10 | Rutas (**TDD**) | `ModelLocation.kt`: `filePath(dir, subdir, name)` y `adbPushHint(applicationId, subdir, name)` para `models/` y `manuales/`; tests actualizados | Verde |
| 3.11 | Métricas (**TDD**) | `Metrics.format` gana `searchMs` y `manualTokens`: `"… · búsqueda 0,2 s · manual 262 tok"` | Verde |
| 3.12 | Flujo en `Assistant` | Carga: motor → Qwen + `setSystemPrompt(PromptBuilder.SYSTEM_PROMPT)` → `Embedder.load` → `ManualStore` + `MetaCheck`. Por pregunta: `embed("query: " + q)` → `ftsIds` + coseno → `rrf` → `select` → **publicar fuentes y aviso** → `resetConversation()` → `sendUserPrompt(userTurn, 400)`. Al terminar, log `ZCA_RESPUESTA` con pregunta, páginas y respuesta completa (para la medición de E5). `release()` libera también e5 | En el A53 con la UI de hoy: B01 y una pregunta coloquial responden con fuentes |
| 3.13 | Carga al móvil | `tools/cargar-movil.ps1` (PowerShell): `adb push` de `models/qwen2.5-1.5b-instruct-q4_k_m.gguf` y `models/multilingual-e5-small-q8_0.gguf` a `files/models/`, y de `models/acs355.sqlite` a `files/manuales/`; compara `adb shell sha256sum` con `Get-FileHash`. Solo copia lo que falta o ha cambiado | Tres líneas `OK <sha>` |
| 3.14 | Verificación en el A53 | 3 preguntas de la batería con la UI mínima; logcat `ZCA`, `ZCA_METRICS`, `ZCA_RESPUESTA` | Fuentes correctas, TTFT en el orden de ~10 s, sin cierres |
| 3.15 | Licencias | `docs/licencias.md`: Qwen2.5-1.5B-Instruct (Apache 2.0), multilingual-e5-small (MIT), llama.cpp (MIT), androidx.sqlite (Apache 2.0); el texto del manual de ABB se usa **solo internamente para la demo**; antes de cualquier uso fuera, consultarlo (no es una conclusión legal) | — |
| 3.16 | Guardia de estrés | `tools/estres-movil.ps1`, versionado, a partir de `llenar-contexto.ps1` del plan 01 (scratchpad de la sesión `b3f7d4d4`; si ya no existe, se reescribe con esta descripción). Parámetros `-Preguntas 20 -TimeoutPorPregunta 120`. Por cada pregunta (lista fija **sin tildes**, porque `adb input text` no las admite; mezcla de códigos, síntomas, seguridad, parámetros y fuera del manual): localizar el `EditText` con `uiautomator dump` **solo con la app parada**, escribir con `adb input text` (espacios como `%s`), buscar el botón "Preguntar" **después** de escribir (el teclado lo mueve), pulsarlo y esperar a que aparezca una línea nueva de `ZCA_METRICS`, `Error al generar` o `Failed` en `adb logcat -d`, sin tocar la interfaz mientras genera. Al final: resumen de logcat filtrado por `FATAL`, `decode.*fail`, `Error`, `Failed`, `STOP: hitting` y `dumpsys meminfo`. Es la guardia de la lección 1 del informe de lecciones del plan 01: el protocolo en verde no ejercitaba los fallos del JNI | Primera ejecución en el A53: 20/20 con métricas, sin `FATAL` ni fallos de `decode` |

**Interfaces de `app` (Kotlin):**

```kotlin
data class Chunk(val id: Long, val page: Int, val chapter: String, val text: String, val tokens: Int)
data class ManualMeta(val manual: String, val version: String)
data class Source(val page: Int, val chapter: String, val text: String)
data class SafetyNotice(val text: String, val page: Int)

object QueryTerms { fun literals(question: String): List<String>; fun ftsQuery(question: String): String? }
object ManualSearch {
    fun cosineTopK(query: FloatArray, vectors: FloatArray, dim: Int, k: Int): List<Int>   // índices
    fun rrf(rankings: List<List<Long>>, k: Int = 60): List<Long>
    fun select(ids: List<Long>, tokens: Map<Long, Int>, maxTokens: Int = 300,
               maxChunks: Int = 2, header: Int = 8): List<Long>
}
class ManualStore(path: File) : AutoCloseable {
    fun meta(): Map<String, String>?
    val chunks: List<Chunk>; val vectors: FloatArray                // cargados al abrir
    fun ftsIds(query: String, limit: Int = 10): List<Long>
}
sealed interface MetaResult {
    data class Ok(val meta: ManualMeta) : MetaResult
    data class Missing(val hint: String) : MetaResult
    data class Incompatible(val reason: String, val hint: String) : MetaResult
}
object MetaCheck { fun check(meta: Map<String, String>?, hint: String): MetaResult }
object PromptBuilder { const val SYSTEM_PROMPT: String; fun userTurn(question: String, chunks: List<Chunk>): String }
object SafetyRules { fun check(question: String, chunks: List<Chunk>): SafetyNotice? }
object SourceList { fun from(chunks: List<Chunk>): List<Source> }
sealed interface Block {
    data class Heading(val level: Int, val spans: List<Span>) : Block
    data class Paragraph(val spans: List<Span>) : Block
    data class ListItem(val ordered: Boolean, val number: Int?, val spans: List<Span>) : Block
}
data class Span(val text: String, val bold: Boolean = false)
object MarkdownLite { fun parse(text: String): List<Block> }
```

**Casos de `SafetyRules`:**

| Entrada | Esperado |
| --- | --- |
| Pregunta `¿Cómo mido la tensión del bus de continua?` | Aviso de la p. 18 |
| Pregunta `Voy a desmontar el cable del motor` | Aviso |
| Pregunta `¿Qué hace el parámetro 1202?`, chunks sin disparadores | `null` |
| Pregunta neutra, pero un chunk contiene `capacitors discharge` o `input power is applied` | Aviso |
| Pregunta en mayúsculas o sin tildes (`TENSION DEL BUS`) | Aviso (comparación sin tildes ni mayúsculas) |

Disparadores (sin tildes, en minúsculas): en la pregunta — `tension`, `bus de continua`,
`condensador`, `desmont`, `medir`, `mido`, `cable del motor`, `brk`, `dc bus`, `voltage`; en los
chunks — `capacitors discharge`, `input power is applied`, `dc bus`, `electricity warning`,
`instructions in chapter safety`, `disconnect it from the ac power` (los dos últimos añadidos el
2026-09-14 para B09, cambiar el ventilador: salen en 5 y 1 chunks del índice; `warning!` se
descartó por salir en 38). Caso de test añadido: pregunta neutra con un chunk que contiene
`disconnect it from the AC power source` → aviso.
Texto del aviso: *"Antes de intervenir: corte la alimentación, espere 5 minutos a que se
descarguen los condensadores y compruebe con un multímetro que no hay tensión."* (p. 18).

**Casos de `MarkdownLite`:**

| Entrada | Esperado |
| --- | --- |
| `**Causa**: sobretemperatura` | `Paragraph[Span("Causa", bold), Span(": sobretemperatura")]` |
| `1. Corte la alimentación` | `ListItem(ordered = true, number = 1, …)` |
| `- Revise el ventilador` / `* Revise…` | `ListItem(ordered = false, number = null, …)` |
| `### Comprobaciones` | `Heading(3, …)` |
| `**sin cerrar` (llega a medias en streaming) | `Paragraph[Span("**sin cerrar")]`: se pinta literal hasta que se cierre, sin parpadeo ni excepción |
| Texto vacío | `[]` |

### Etapa 4 — Pantalla "instrumento de taller" (1,5–2 días)

**Misma rama.**

| # | Paso | Detalle | Verificación |
| --- | --- | --- | --- |
| 4.1 | Mockup **[Francisco]** | Skill `design`: pantalla de chat en 4 estados, solo tema oscuro cálido, mono para códigos y páginas, acento ámbar, sin burbujas. (1) Listo: manual y versión en la cabecera, aviso de preguntas independientes. (2) Consultando: pregunta, fuentes ya visibles y "procesando el manual… 6 s". (3) Respuesta: Markdown con pasos, tarjeta de seguridad arriba, "Fuentes: p. 362 · p. 360" con un fragmento desplegado, marca de respuesta cortada al final. (4) Falta modelo o manual: qué falta y el comando de `adb push`. **Francisco lo aprueba antes de escribir Compose** | Aprobación escrita; tokens en `docs/diseno-ui.md` |
| 4.2 | Tema | `ui/Theme.kt` con los colores y tipografías aprobados (`FontFamily.Monospace` salvo que el mockup exija otra fuente con licencia OFL) | — |
| 4.3 | Pantalla | `ui/ChatScreen.kt` sale de `MainActivity.kt` (que se queda con el ciclo de vida): cabecera con estado y manual, hilo, campo y botón "Preguntar" | Compila; los tests siguen verdes |
| 4.4 | Markdown | `ui/MarkdownText.kt` pinta los `Block` de `MarkdownLite` | Negritas y listas bien en el A53 |
| 4.5 | Fuentes | `ui/SourcesRow.kt`: chips mono por página; al tocar, despliega el fragmento (inglés) con página y capítulo | — |
| 4.6 | Seguridad | `ui/SafetyCard.kt`: tarjeta ámbar encima de la respuesta, con la cita de la p. 18 tocable | — |
| 4.7 | Espera | Estado `Assistant.phase` (`Searching` / `Processing(startedAt)` / `Generating`) y contador de segundos en "procesando el manual…" | El contador avanza y desaparece con el primer token |
| 4.8 | Ficheros que faltan | Estado (4) del mockup a partir de `MetaResult` y de los ficheros de modelos ausentes | Borrar el `.sqlite` del móvil → la pantalla muestra el `adb push` exacto |
| 4.9 | Scroll final | Hoy la vista solo sigue mientras genera (`MainActivity.kt`, `snapshotFlow` sobre `scroll.maxValue` con `generating`). Añadir un último desplazamiento cuando `generating` pasa a `false`, para que se vea la marca "(respuesta cortada…)" | Una respuesta que llega al tope deja la marca visible sin tocar la pantalla |
| 4.10 | Revisión visual | Tras terminar cada respuesta (nunca durante): `adb exec-out screencap -p` y comparar con el mockup | Capturas de los 4 estados en el informe |
| 4.11 | Commits | `feat(ui): …` por pieza | — |

### Etapa 5 — Medición, revisión y cierre (1 día)

**Misma rama.**

| # | Paso | Detalle | Verificación |
| --- | --- | --- | --- |
| 5.1 | Condiciones | Batería > 50 %, sin cargar, 2 min de reposo, app recién abierta y modelo cargado. Wi-Fi activo por el ADB: la app no tiene permiso `INTERNET`, así que la red no interviene (se deja escrito, con el manifiesto como prueba) | Anotadas en el informe |
| 5.2 | Batería en el A53 | Claude genera desde `docs/bateria-manual.yaml` un fichero con las 12 preguntas, **en HTML con un párrafo por pregunta** (en el plan 01 el `.txt` no ajustaba las líneas en el visor), lo copia a `/sdcard/Download/` y lo abre como en el plan 01: `_id` con `content query` en MediaStore y `am start --grant-read-uri-permission -n com.android.htmlviewer/.HTMLViewerActivity -d content://media/external/file/<id>`. **Francisco copia y pega cada pregunta** en la app (`adb input text` no escribe tildes). Claude lee `ZCA_METRICS` y `ZCA_RESPUESTA` al terminar cada una, sin tocar la interfaz durante la generación | Fichero abierto en el móvil; 12 líneas de métricas y 12 respuestas completas |
| 5.3 | Seguridad, memoria y estrés | Tarjeta en las preguntas `riesgo: true`. Después de la batería y **sin reiniciar la app**: `tools/estres-movil.ps1 -Preguntas 20` (reinicio de la caché, tope de 400 tokens y fuentes, 20 veces seguidas). Al final, minimizar 60 s y volver; `dumpsys meminfo` antes y después del estrés | 20/20 respuestas, sin `FATAL` ni fallos de `decode`, heap estable (sin crecer pregunta a pregunta). Anotado en el informe |
| 5.4 | Rúbrica | Claude propone la nota 0–2 de cada respuesta con el motivo; **Francisco la valida** | Tabla con 12 notas |
| 5.5 | Informe | `informes/AAAA-MM-DD-rag-manual-a53.md` con la plantilla de §7, incluidas las Incidencias de E2–E5 | — |
| 5.6 | **Gate G4 [Francisco]** | Aplicar §3 y escribir la recomendación | Decisión de Francisco escrita |
| 5.7 | Revisión de código | `simplify` sobre el código propio; después `/code-review` del diff de la rama y `receiving-code-review` para cada hallazgo (verificarlo antes de aplicarlo) | Hallazgos resueltos o justificados en el informe |
| 5.8 | Documentación | `README.md` (cómo generar el índice, cargar el móvil, leer métricas) y `CLAUDE.md` del repo (tools/, RAG, prompt de sistema, batería, CI) | — |
| 5.9 | PR, CI y tag | `finishing-a-development-branch`; `gh pr create`; CI verde con los tests `PASSED` en el log. Tras la fusión: `git tag -a v0.2.0-rag-manual -m "…"`; **Francisco** `git push --follow-tags` | Tag en GitHub |
| 5.10 | Lecciones | Skill `lecciones-aprendidas` a partir de las Incidencias, con el enfoque de las charlas de Francisco: qué decidió el proceso (checkpoints, verificación), qué supuso Claude sin comprobar y qué lo destapó | Informe en su repo (directo a `main`, excepción de la política) |
| 5.11 | Skill propia (condicional) | Si hubo ≥ 3 incidencias repetibles (cargar el móvil, leer métricas, apps duplicadas, recuperar tras LMK): `writing-skills` para `zca-android` | Probada en una ejecución limpia |

---

## 6. Protocolo con el manual

### Prompt de sistema (en este plan y en `PromptBuilder.kt`; si cambia, en los dos sitios)

> Eres un asistente técnico industrial para el variador ABB ACS355. Responde siempre en español,
> de forma breve y con pasos numerados cuando haya que hacer algo. Usa solo los fragmentos del
> manual que acompañan a la pregunta. Si no contienen la respuesta, di: "No aparece en el
> manual". No escribas números de página. Si hay riesgo eléctrico, avisa primero.

### Turno del usuario (formato exacto de `userTurn`)

```text
Fragmentos del manual (en inglés):
[1] Fault tracing
<texto del chunk 1>
[2] Fault tracing
<texto del chunk 2>

Pregunta: <pregunta>
```

Sin chunks: la línea `Fragmentos del manual (en inglés): (sin fragmentos)`. La cabecera lleva el
capítulo, **no** la página, para no invitar al modelo a escribir números de página.

### Batería (`docs/bateria-manual.yaml`)

Reparto: 4 de códigos de fallo o alarma (una es **F0009 → p. 362**), 3 de síntomas coloquiales
(una es el armario caliente → DEV OVERTEMP, p. 360), 2 de seguridad o procedimiento (una es la
**medida de la tensión del bus de continua**, con la p. 18 entre las esperadas), 2 de parámetros
y 1 de algo que **no** está en el manual.

```yaml
- id: B01
  tipo: codigo            # codigo | sintoma | seguridad | parametro | fuera
  pregunta: "El variador muestra el fallo F0009. ¿Qué significa y qué reviso?"
  paginas_esperadas: [362]
  riesgo: false           # true → la tarjeta de seguridad es obligatoria (G4)
  claves: ["sobretemperatura del motor", "MOT OVERTEMP"]   # lo que una respuesta correcta debe decir
```

### Rúbrica (0–2 por pregunta)

- **2**: en español; dice lo que dice el manual (las `claves`); pasos numerados si hay que hacer
  algo; nada inventado; la página esperada está entre las fuentes.
- **1**: correcta pero incompleta, o con partes en inglés, o con la página esperada fuera de las
  fuentes pero el contenido bien.
- **0**: inventa o contradice el manual, otro idioma, vacía o cortada antes de decir nada útil.
- **Pregunta fuera del manual**: 2 si dice que no aparece en el manual sin inventar datos
  concretos; 0 si se los inventa (y además es NO-GO en G4).

---

## 7. Plantilla de informe (`informes/`)

```text
# <qué> — <fecha>
Móvil: modelo, Android, MemAvailable en reposo, batería %, permisos de red de la app
App: commit | llama.cpp b10941 | Qwen2.5-1.5B Q4_K_M (SHA256) | e5-small Q8_0 (SHA256) | acs355.sqlite (meta.version)
Parámetros: n_ctx, hilos, temp, max tokens, presupuesto de manual, chunks por pregunta

| ID | Tipo | Páginas esperadas | Fuentes mostradas | Búsqueda (ms) | Manual (tok) | TTFT (s) | tok/s | Tarjeta | Nota (0-2) | Motivo |
(12 filas + fila de medianas)

Memoria: heap y MemAvailable con los dos modelos; minimizar 60 s
Incidencias: escritas en el momento (qué se supuso, qué pasó, cómo se detectó, coste)
Decisión según §3: GO / GO con reservas / NO-GO — y por qué
Gate: decisión de Francisco
```

---

## 8. Fuera de este plan

Botón "Importar" en la app, instalación por MDM o Play privado, descarga desde un servidor de la
empresa (introduce red y un secreto), visor del PDF en la página citada (pedido por Francisco el
2026-09-14 para después del MVP: cada referencia de página, en los chips de fuentes y en la cita
de la tarjeta de seguridad, es un enlace que abre el PDF en esa página), publicar la página de
la charla (`tools/charla/`, un solo HTML) en un hosting propio como Hostinger (pedido por
Francisco el 2026-09-14; antes, decidir qué hacer con el texto del manual de ABB que lleva, ver
`docs/licencias.md`), varios manuales,
memoria de conversación, tema claro, OCR de la pantalla del variador, voz, bitácoras de trabajo.
El móvil con i8mm solo entra si G4 da NO-GO en tiempo. La tabla `meta` deja preparado el cambio
de manual sin tocar la app.

**Pendientes para producción, anotados en G3** (lista con datos en
`informes/2026-09-14-indice-manual.md`): modelo de vectores más capaz (e5-base), preguntas
coloquiales, batería escrita por técnicos ajenos, fusión RRF, extracción de tablas que respete el
diseño y entradas largas partidas. El MVP es para una demo de 5 minutos en una charla.

---

## 9. Skills y herramientas: cuáles, cuándo y cuáles no

### 9.1 Entran en este plan

| Skill / herramienta | Cuándo (paso) | Para qué |
| --- | --- | --- |
| `superpowers:brainstorming` | Hecho (2026-09-14) | Alcance y decisiones, pregunta a pregunta |
| `superpowers:writing-plans` | Hecho (este documento) | Adaptado a `plan/` numerado y a la estructura del plan 01 |
| `superpowers:executing-plans` | Toda la ejecución | Etapa a etapa, parando en 1.10, 1.14, 4.1 y 5.6 |
| `superpowers:test-driven-development` | 1.3–1.8, 1.11, 3.1–3.11 | Troceado, términos, RRF, presupuesto, meta, prompt, seguridad, fuentes, Markdown, métricas |
| `superpowers:verification-before-completion` | Cierre de cada etapa y antes de cada PR | "Hecho" = verificado en el A53 o en el runner; en la CI, el test aparece `PASSED` en el log |
| `superpowers:systematic-debugging` | Ante cualquier fallo | JNI, SQLite, CI, memoria. Nada de parchear a ciegas |
| `design` | 4.1 | Mockup de los 4 estados |
| `simplify` | 5.7 | Esta vez hay mucho código propio |
| `superpowers:requesting-code-review` + `/code-review` | 5.7 | Diff de `feat/rag-app` |
| `superpowers:receiving-code-review` | Tras 5.7 | Verificar cada hallazgo antes de aplicarlo |
| `superpowers:finishing-a-development-branch` | 0.5, 1.15, 5.9 | Cierre de las tres ramas; comando de push para Francisco |
| `lecciones-aprendidas` | 5.10 y tras cualquier atasco > 1 h | A partir de las Incidencias; enfoque de charla |
| `context7` (MCP) | 2.2, 2.3, 4.x | API de androidx.sqlite, embeddings en llama.cpp b10941, Compose |
| `run` | 2.x–5.x | Lanzar la app en el A53 de forma repetible |
| `git-doctor` | Si `git`, `gh`, `ssh` o Docker se cuelgan o piden credenciales | Diagnóstico por síntomas |
| `update-config` / `fewer-permission-prompts` | Inicio de E0 si hay prompts repetidos | Permitir `uv`, `docker`, `gh run` sin prompt |
| `superpowers:writing-skills` | 5.11, condicional | Skill `zca-android` solo con dolor repetido documentado |

### 9.2 Quedan fuera, y por qué

| Skill / herramienta | Por qué no |
| --- | --- |
| `security-review` | Sin red, sin secretos, sin entrada externa: los ficheros llegan por `adb` desde el PC de Francisco. Entra si algún día hay botón Importar o descarga desde servidor |
| `dataviz` | Los informes van con tablas; 12 filas se leen mejor así |
| `superpowers:using-git-worktrees` | Una rama activa a la vez; el worktree duplicaría el submódulo de llama.cpp |
| `superpowers:subagent-driven-development` / `dispatching-parallel-agents` | Etapas secuenciales y un único móvil físico |
| `claude-api` | Todo es local y gratis |
| `artifact-design`, `artifact-diagramming`, `artifact-capabilities` | La publicación del mockup la gestiona `design`; los informes son Markdown en el repo |
| `loop`, `schedule` | La CI se sigue con `gh run watch`; no hay nada periódico que vigilar |
| `init` | `CLAUDE.md` ya existe; se actualiza a mano en 5.8 |
| `keybindings-help`, `statusline-setup` | Configuración del editor, ajena al proyecto |

---

## 10. Verificación final del plan

1. CI con `android-unit` y `tools` como checks obligatorios de `main`; el log de un PR muestra los
   tests `PASSED`.
2. `models/acs355.sqlite` generado desde el PDF con `uv run zca-indice construir`; `meta` completa.
3. Informe de G3 con recall@2 de FTS5, vectores e híbrida, y decisión de Francisco.
4. En el A53: coseno móvil–PC ≥ 0,99 en las 12 preguntas; FTS5 funcionando; e5 < 0,3 s.
5. Informe de G4 con las 12 preguntas, TTFT, tok/s, notas validadas, tarjeta de seguridad,
   memoria, la tanda de estrés (20/20, sin `FATAL`), Incidencias y decisión de Francisco.
6. Pantalla igual al mockup aprobado en los 4 estados (capturas en el informe).
7. `git ls-files | Select-String -Pattern 'gguf|pdf|sqlite'` vacío. `main` solo con merges de PR.
8. Tag `v0.2.0-rag-manual` en GitHub. Informe de lecciones en su repo.

## 11. Riesgos

| Riesgo | Mitigación |
| --- | --- |
| `sqlite-bundled` no trae FTS5, o su versión exige AGP 9 / SDK 37 (como el BOM de Compose) | Es lo primero que se comprueba (2.2); alternativa `requery/sqlite-android` |
| Vectores distintos en PC y móvil (tokenizador, pooling, normalización) | Mismo GGUF en los dos lados; coseno ≥ 0,99 en 2.3 antes de seguir |
| Qwen + e5 en memoria → Android mata la app | e5 con mmap y sin repack; se libera en `onStop`; si pasa, liberar e5 entre preguntas y después probar sin repack |
| El troceado parte mal las tablas (continuaciones que empiezan por número) | Caso de test específico en 1.5; G3 lo detecta; una ronda de ajuste |
| Qwen responde en inglés al leer chunks en inglés, o escribe páginas | Prompt de sistema explícito (§6), cabeceras sin página, rúbrica que lo penaliza; las fuentes las pone la app |
| El runner de Linux exige NDK/CMake o el submódulo para configurar `lib` | Reproducción en Docker en 0.4 antes de pedir el push |
| La regla de términos diverge entre Python y Kotlin | Misma tabla de casos en los dos tests (§5, E1) |
| Falsos positivos de la tarjeta de seguridad (sale en preguntas de parámetros) | Caso de test negativo en 3.6; se anotan en el informe de G4 |
| Texto del manual de ABB en la app | Uso interno para la demo; `docs/licencias.md` lo deja escrito |
| Samsung vuelve a clonar la app en cada `install -r` (usuario 95) | `pm uninstall --user 95` tras instalar (restricciones globales) |
| TTFT fuera de umbral | Regla 1 (bajar a ~200 tokens) y regla 2 (móvil con i8mm) de §3 |
