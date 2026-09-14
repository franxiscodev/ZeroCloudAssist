# Piezas nativas en el A53 (etapa 2 del plan 02) — 2026-09-14

Rama `feat/rag-app` desde `main` (`cc92d74`, etapa 1 fusionada y G3 = GO con reservas acotado al
MVP). Móvil por USB (con la depuración inalámbrica también conectada: los comandos llevan
`adb -s R5CT30HSZTT`).

Móvil: Samsung Galaxy A53 (SM-A536E), Android 16 (`BP2A.250605.031.A3.A536EXXSNGZG3`), batería
100 % con USB conectado, MemAvailable ~1,95 GB en reposo. La app no pide `INTERNET` ni ningún otro
permiso de red.

## Ficheros en el móvil

`tools/cargar-movil.ps1 -Serial R5CT30HSZTT` (forma mínima del paso 3.13), SHA256 comprobado en
el móvil con `sha256sum`:

| Fichero | Destino (`files/`) | SHA256 |
| --- | --- | --- |
| `acs355.sqlite` (versión `2026-09-14.2`) | `manuales/` | `588dd909e3602feddec4906d6c1e8e97b3c9ae0cfd8787207deaafa506cdae6d` |
| `multilingual-e5-small-q8_0.gguf` | `models/` | `7605c7f0022cb972dbb8b05e69ff65ac0db4a3b4756f0f8a88aa8280485c5505` |
| `bateria-vectores.json` (solo depuración) | `debug/` | `dfd2ff2a297a1f671eaff1695da0b56d73be07c7508ffa1c384db1d69ce9912c` |

## 2.2 SQLite empaquetado

`androidx.sqlite:sqlite-bundled` 2.7.1 (última estable; pide `kotlin-stdlib` 2.1.20, el proyecto
va con Kotlin 2.3.0) entra sin tocar AGP 8.13.2 ni `compileSdk` 36. Abre el índice en solo
lectura y tiene FTS5: no hace falta `requery/sqlite-android`.

Consulta del plan (`MATCH '"0009"' LIMIT 3`), móvil y PC (SQLite 3.47.1 de Python) idénticos:
ids 791 (p. 250), 873 (p. 269), 1199 (p. 354). Con `ORDER BY rank` y sin límite, también
idénticos: 791 (p. 250), 1199 (p. 354), **1230 (p. 362)**, 873 (p. 269), 1231 (p. 362). El bm25
del SQLite empaquetado ordena igual que el del PC.

## 2.3 Embedder JNI

e5 se carga detrás de Qwen, en 2,4 s. Log de llama.cpp: `load_mode = mmap`,
`CPU_Mapped model buffer size = 119.19 MiB`, **sin `CPU_REPACK`**; aparece
`CPU_KLEIDIAI model buffer size = 21.52 MiB` (KleidiAI reempaqueta una parte pequeña en memoria
anónima). No se desactiva `use_extra_bufts`: el plan lo pide solo si sale `CPU_REPACK`, y 21,5 MB
no cambian el presupuesto de §2b. Contexto: n_ctx 512, pooling 1 (mean), 6 hilos.

| ID | Coseno móvil–PC | ms |
| --- | --- | --- |
| B01 | 0,9994 | 61 |
| B02 | 0,9994 | 30 |
| B03 | 0,9996 | 21 |
| B04 | 0,9995 | 21 |
| B05 | 0,9995 | 116 |
| B06 | 0,9996 | 17 |
| B07 | 0,9995 | 26 |
| B08 | 0,9994 | 77 |
| B09 | 0,9995 | 51 |
| B10 | 0,9995 | 42 |
| B11 | 0,9995 | 34 |
| B12 | 0,9994 | 91 |

Mínimo 0,9994 (pide ≥ 0,99) y máximo 116 ms (pide < 0,3 s): **pasa**. Medido con la app recién
abierta y Qwen cargado sin generar; los tiempos irregulares (17–116 ms) son de hilos y frecuencia
de la CPU, no del texto.

## 2.4 Reiniciar al prompt de sistema

`resetConversation()` (Kotlin, solo en `ModelReady`) → `resetToSystemPrompt` en `ai_chat.cpp`:
`llama_memory_seq_rm(mem, 0, system_prompt_position, -1)`, `current_position =
system_prompt_position`, como en el plan. Además recorta `chat_msgs` al mensaje de sistema: sin
eso, la plantilla formatea la pregunta siguiente como continuación del turno borrado. `Assistant`
lo llama antes de cada pregunta.

Dos preguntas seguidas desde la pantalla ("Que significa el fallo F0009", "Y el fallo F0001"):

| Pregunta | Log de `processUserPrompt` | TTFT | tok/s |
| --- | --- | --- | --- |
| 1 | 19 tokens from position **47** (system prompt ends at 47) | 3,5 s | 9,6 |
| 2 | 18 tokens from position **47** (system prompt ends at 47) | 0,8 s | 9,2 |

La segunda empieza en la posición 47 y no a continuación de la primera (47 + 19 + 200): **pasa**.
Las dos respuestas llegan al límite de 200 tokens del plan 01. Sin RAG todavía, así que no hay
fragmentos del manual en el prompt.

## 2.5 Memoria

`dumpsys meminfo com.ialogia.zerocloudassist` y `/proc/meminfo`, con Qwen + e5 cargados y el
índice abierto (y cerrado) por las comprobaciones de depuración:

| Momento | PSS total | Native Heap (PSS) | Other mmap (GGUF) | Swap PSS | MemAvailable |
| --- | --- | --- | --- | --- | --- |
| Cargados, ~10 min en reposo tras dos preguntas | 1 342 MB | 984 MB | 13 MB | 267 MB | 1 063 MB |
| Minimizada 60 s (`Modelo liberado`, Qwen y e5 fuera) | 95 MB | 24 MB | 0,1 MB | 14 MB | 2 740 MB |
| De vuelta, recién cargados | 2 375 MB | 1 270 MB | 1 020 MB | 14 MB | 1 841 MB |

- Mismo proceso (pid 32437) antes, durante y después de minimizar, sin cierres ni `FATAL`.
  Al volver carga Qwen en 5,1 s y e5 en 2,1 s, y las comprobaciones 2.2 y 2.3 vuelven a pasar
  (coseno mínimo 0,9994, máximo 72 ms). **Pasa.**
- "Other mmap" son las páginas de los GGUF leídas por mmap: recién cargados cuentan ~1 GB en el
  PSS, pero son de fichero y el sistema las recupera (a los 10 min quedaban 13 MB). Lo que no se
  recupera es el heap nativo, y parte de él (267 MB) ya estaba en swap (zram).
- Heap nativo que informa la app al responder (`ZCA_METRICS`): **1 606 MB**, frente a ~1,4 GB del
  plan 01 con solo Qwen. e5 declara ~28 MB (21,5 KleidiAI + 5,8 cálculo + 1 salida); el resto de
  la diferencia no está explicado. No ha hecho falta liberar e5 entre preguntas. Se vigila en la
  tanda de estrés (5.3).

## Incidencias

1. **La verificación de 2.2 esperaba la p. 362 con una consulta que no podía darla.** Se supuso
   que `LIMIT 3` bastaba para ver la entrada de F0009. La consulta del plan no ordena, así que
   devuelve las tres primeras coincidencias por id, y la p. 362 es la cuarta (por relevancia, la
   tercera). Se detectó porque el móvil devolvió 250, 269 y 354; la misma consulta en el PC dio
   exactamente esas filas, así que el SQLite del móvil estaba bien. Coste: una consulta en el PC.
   La comprobación pasa a `ORDER BY rank` sin límite. En la app no afecta: `ftsIds` (paso 3.3) ya
   toma todas por rank y `prioritize` sube la entrada que define el código.
2. **`embedder.cpp` no compiló al primer intento: `logging.h` del ejemplo no es autónomo.** Usa
   `ggml_log_level` sin incluir `ggml.h`; en `ai_chat.cpp` funciona porque antes se incluye
   `sampling.h`. Se supuso que se podía incluir el primero. Error de compilación claro
   (`incomplete type 'enum ggml_log_level'`); se incluye detrás de `llama.h` sin tocar el fichero
   del ejemplo. Coste: una compilación (~1 min). Añadir un `.cpp` a `CMakeLists.txt` recompila
   llama.cpp entero (~5 min): conviene juntar esos cambios.
