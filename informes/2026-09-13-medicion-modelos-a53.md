# Medición de modelos en el Samsung A53 con PocketPal — 2026-09-13

Plan 01, etapa 2C. Objetivo: tok/s, TTFT, carga y memoria de cada modelo en el móvil real,
antes de escribir código propio. Criterios go/no-go en §3 del plan.

**Estado: en curso.** Las celdas `—` se rellenan durante la medición.

## Condiciones

| Campo | Valor |
| --- | --- |
| Móvil | Samsung Galaxy A53 5G `SM-A536E`, Android 16 / API 36 |
| SoC | Exynos 1280: 2× Cortex-A78 + 6× Cortex-A55; dotprod sí, i8mm no |
| RAM disponible en reposo | 2,13 GB (de 5,5 GB), zram 8 GB |
| Batería / temperatura al empezar | 91 %, sin cargar, 35,8 °C |
| Red durante la generación | Modo avión, Wi-Fi y Bluetooth apagados |
| App | PocketPal AI `com.pocketpalai` 1.17.3, instalada desde Google Play |
| Modelos | Los de `docs/modelos.md`, SHA256 verificado en el móvil tras copiarlos |

## Ajustes de PocketPal

Se parte de los valores por defecto de la app (leídos en su código, v1.17.3,
`src/utils/contextInitParamsVersions.ts`) y solo se cambia lo que exige el protocolo.

Todos los valores de la columna "Usado" se **verificaron leyendo la pantalla del móvil** con
`uiautomator dump`, no a ojo: Settings (contexto 2048, "Using 4 of 8 available threads"),
hoja "Chat Generation Settings (Preset)" (N PREDICT Custom 200, TEMPERATURE 0.2, TOP K 40,
TOP P 0.95) y Model Settings (system prompt de 170 caracteres idéntico al del protocolo; el
teclado de Samsung había añadido dos espacios finales al guardar la primera vez y se quitaron).
El log de carga confirma `n_threads=4, n_threads_batch=4`.

| Ajuste | Por defecto | Usado | Motivo |
| --- | --- | --- | --- |
| Context Size | 2048 | 2048 | Protocolo |
| CPU Threads | 6 (80 % de 8 núcleos) | 4, luego 2 y 6 | Protocolo: se mide la curva |
| Batch / Physical Batch | 512 / 512 | igual | — |
| Flash Attention | Off en Android | igual | — |
| Cache K / V | f16 / f16 | igual | — |
| Memory Mapping | **Disabled** en Android | igual | Lo recomendado por la app junto a Weight Repacking |
| Weight Repacking | On | igual | Acelera el procesado del prompt |
| Auto Offload/Load | On | igual | No cambiar de app durante la medición |
| Display Memory Usage | — | — | **No existe en Android**: el interruptor solo se muestra en iOS (`Platform.OS === 'ios'` en `SettingsScreen.tsx`). La memoria se mide con `adb shell dumpsys meminfo com.pocketpalai` |
| System prompt | vacío | el de §5 del plan (verificado carácter a carácter) | Protocolo |
| Stop words | `</s>` | `</s>`, `<\|im_end\|>` | Fin de turno de Qwen |
| Temperatura | 0,7 | 0,2 | Protocolo |
| Tokens de salida (n_predict) | ilimitado | 200 | Protocolo |

**Desviación respecto al plan §2b.** El plan asumía pesos por mmap. PocketPal en Android los
carga en memoria anónima (mmap desactivado + repacking), así que el modelo entero cuenta contra
los 2,13 GB disponibles. Es el caso más exigente; si pasa aquí, pasa con mmap.

## Memoria

| Momento | PocketPal PSS / RSS | `MemAvailable` del sistema |
| --- | --- | --- |
| Reposo, todas las apps cerradas | — | 2,13 GB |
| PocketPal abierto, **sin modelo** | 233 MB / 318 MB | 1,99 GB |
| Modelo cargado, antes de generar | **1 416 MB / 1 449 MB** (Native Heap 1 245 MB = pesos; swap PSS 45 MB) | **1,40 GB** (MemFree 143 MB) |
| Pico durante la generación | — | — |

Presupuesto previsto para Qwen2.5-1.5B: 1,12 GB de pesos en memoria anónima + ~59 MB de caché
KV (28 capas × 2 cabezas KV × 128 dim × 2048 tokens × K y V en f16) + 150–300 MB de buffers de
cómputo ≈ 1,35–1,5 GB sobre los 1,99 GB disponibles.

Justo antes de activar el modo avión (20:55:27 hora del móvil, mismo proceso de PocketPal,
pid 32233, con el modelo cargado desde las 20:45:57): `MemAvailable` 1,20 GB, swap libre
6,1 GB, batería 88 % sin cargar, 29,9 °C.

## Tanda 1 (20:58–21:04): no válida para la decisión

Modo avión, 4 hilos. Solo se hicieron 3 de las 9 ejecuciones, **las tres en el mismo chat**
(P2 y P3 llevan la conversación anterior en el contexto), y el modelo se recargó dos veces.

| Prompt | tok/s | ms/token | TTFT | Calidad (0-2) | Observación |
| --- | --- | --- | --- | --- | --- |
| P1 variador | 8,19 | 122 | 4 883 ms | 2 | Definición correcta; termina sola |
| P2 F0009 | 8,96 | 112 | 6 670 ms | 1 | Pasos numerados, pero consejos sin sentido ("armario en contacto con el suelo", "motor en posición normal o reversa"); no menciona ventilación. Cortada a 200 tokens. TTFT inflado: tras una recarga el contexto se procesó entero |
| P3 bus CC | 8,08 | 124 | 3 522 ms | 1 | Menciona el riesgo eléctrico pero no manda cortar tensión ni esperar la descarga; relleno ("cómpralo en una tienda"). Cortada |

El log confirma que el system prompt se aplicó: los tokens del prompt empiezan por
`<|im_start|>system` y el texto del protocolo.

### Tiempos exactos (exportación JSON de PocketPal)

"Export current session" guarda en Descargas los metadatos de cada respuesta, con los
`timings` de llama.rn completos. Copia en
`informes/2026-09-13-medicion-modelos-a53/tanda1-export.json`.

| Prompt | `prompt_n` | `prompt_ms` | Procesado (tok/s) | `predicted_n` | Generación (tok/s) | TTFT (ms) |
| --- | --- | --- | --- | --- | --- | --- |
| P1 | 65 | 4 727 | 13,8 | 91 (fin natural) | 8,19 | 4 883 |
| P2 | 195 (conversación entera, tras la recarga) | 6 518 | 29,9 | 199 (límite) | 8,96 | 6 670 |
| P3 | 26 (reutiliza la caché KV) | 3 096 | 8,4 | 199 (límite) | 8,08 | 3 522 |

**Hallazgo principal de la tanda: el cuello de botella es el procesado del prompt, no la
generación.** El tiempo no es proporcional a los tokens: 26 tokens cuestan 3,1 s y 195 cuestan
6,5 s. Eso sugiere un coste fijo de ~3 s por mensaje más ~14–20 ms por token. Hipótesis a
discriminar con el benchmark (pp 512): (a) sobrecoste de la app o del motor por petición,
(b) hilos repartidos en los núcleos lentos A55, (c) procesado del prompt lento en esta CPU sin
i8mm.

**Por qué importa para el plan 02 (RAG).** Cada pregunta llevará 500–1 000 tokens de
fragmentos del manual. Con 14–30 tok/s de procesado serían 20–35 s hasta la primera palabra.
El criterio de TTFT del plan (≤ 2 s con ~150 tokens) está pensado para el chat sin RAG; con RAG
el procesado del prompt será el factor que decida.

### Qué pasó (logcat, eventos del sistema)

| Hora | Evento | Fuente |
| --- | --- | --- |
| 20:58:14–20:58:58 | Durante la generación de P1, el sistema cierra ~12 apps en caché (Play Store, GMS, servicios de Samsung) para liberar memoria. PocketPal, en primer plano, no se toca | `ActivityManager ... has died: cch+5` + `lowmemorykiller process_mrelease` |
| 20:59:34 | PocketPal pasa a segundo plano (tras cerrar la barra de captura de Samsung) → **auto-release** del modelo | `ReactNativeJS: Active → Background: Auto-releasing context` |
| 20:59:53 → 21:00:00.6 | Vuelve a primer plano → **recarga en 7,0 s** | `Reloading auto-released model` + `RNLlama loadModel` |
| 21:04:10 | Segundo plano otra vez | `App state change: active → background` |
| 21:04:18.3 → 21:04:34.9 | Recarga en **16,6 s** (con más presión de memoria) | `RNLlama loadModel` |
| 21:04:49 | **El proceso muere estando en caché** (oom_adj 900), no en primer plano | `am_proc_died [0,32233,com.pocketpalai,900,...]` |
| 21:06:16 | Se reabre PocketPal, sin modelo cargado | `am_proc_start` |

### Lectura

- **Velocidad de generación**: 8,1–9,0 tok/s → en el borde del GO (≥ 8). Hay que confirmarla limpia.
- **TTFT**: no evaluable en esta tanda (recargas y contexto acumulado). P1, sin contaminación,
  dio 4,9 s para ~65 tokens de prompt: ~13 tok/s de procesado, sospechosamente bajo. El
  benchmark (pp 512) dirá si es el procesado del prompt o el primer mensaje tras cargar.
- **Memoria**: en primer plano aguanta, a costa de que el sistema cierre otras apps. En
  segundo plano, con 1,4 GB de PSS, el sistema lo cierra en menos de un minuto.
- **Consecuencia para la etapa 4 (app propia)**: asumir que la app muere al salir a segundo
  plano y que recargar cuesta 7–17 s. Encaja con el paso 4.9 del plan (liberar en `onStop`,
  recargar en `onStart`), pero la recarga debe verse en la UI y no bloquearla.

**Regla para la tanda 2**: no salir de PocketPal en ningún momento. Tras cada captura, cerrar la
barra de Samsung tocando la propia conversación, nunca la miniatura.

## Benchmark integrado (pp 512, tg 128, 3 repeticiones)

| Modelo | Hilos | pp (tok/s) | tg (tok/s) | Memoria pico | Notas |
| --- | --- | --- | --- | --- | --- |
| Qwen2.5-1.5B | 4 | — | — | — | — |
| Qwen2.5-1.5B | 2 | — | — | — | — |
| Qwen2.5-1.5B | 6 | — | — | — | — |

## Prompts del protocolo (§5), un chat nuevo por ejecución

| Modelo | Prompt | Ejec. | TTFT (ms) | tok/s | Calidad (0-2) | Notas |
| --- | --- | --- | --- | --- | --- | --- |
| Qwen2.5-1.5B | P1 variador | 1 | — | — | — | — |
| Qwen2.5-1.5B | P1 variador | 2 | — | — | — | — |
| Qwen2.5-1.5B | P1 variador | 3 | — | — | — | — |
| Qwen2.5-1.5B | P2 F0009 | 1 | — | — | — | — |
| Qwen2.5-1.5B | P2 F0009 | 2 | — | — | — | — |
| Qwen2.5-1.5B | P2 F0009 | 3 | — | — | — | — |
| Qwen2.5-1.5B | P3 bus CC | 1 | — | — | — | — |
| Qwen2.5-1.5B | P3 bus CC | 2 | — | — | — | — |
| Qwen2.5-1.5B | P3 bus CC | 3 | — | — | — | — |

## Carga del modelo

| Modelo | Condición | Tiempo (s) | Cómo se midió |
| --- | --- | --- | --- |
| Qwen2.5-1.5B | primera carga tras importar (templada: fichero recién copiado, probablemente en caché) | **~9,0** | logcat `RNLlama`: `loadModel` 20:45:48.496 → `Context initialized` 20:45:57.465. A ojo pareció "un segundo o menos": la pantalla del chat aparece antes de que termine la carga |
| Qwen2.5-1.5B | recarga tras volver de segundo plano (tanda 1, 20:59:53) | 7,0 | logcat `RNLlama loadModel` → `Context initialized` |
| Qwen2.5-1.5B | recarga con más presión de memoria (tanda 1, 21:04:18) | 16,6 | ídem |
| Qwen2.5-1.5B | tras morir y reabrirse el proceso (21:10:57) | **10,2** | ídem; `n_threads=4`. Memoria después: PSS 1,39 GB, `MemAvailable` 1,19 GB |
| Qwen2.5-1.5B | tras reinicio del móvil (frío) | — | — |

PocketPal no muestra el tiempo de carga; se mide con cronómetro o por las marcas de tiempo de
logcat. La carga no usa red, así que se mide con el Wi-Fi encendido para poder leer logcat.

## Decisión según §3

Pendiente.
