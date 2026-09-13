# Medición de modelos en el Samsung A53 con PocketPal — 2026-09-13

Plan 01, etapa 2C. Objetivo: tok/s, TTFT, carga y memoria de cada modelo en el móvil real,
antes de escribir código propio. Criterios go/no-go en §3 del plan.

**Estado: medición cerrada (tandas 1 a 4).** Resultado y recomendación en la última sección,
"Decisión según §3". El Gate G1 queda pendiente de la decisión de Francisco.

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
| Pico en el benchmark (pp 512 + tg 128) | ~1,3–1,4 GB (24,9–26,7 % de 5,3 GB según PocketPal) | — |

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

> **Corregido en la tanda 2.** Sin recargas del modelo, el procesado del prompt sale
> proporcional a los tokens (~35–55 ms/token, 18–28 tok/s) y el "coste fijo de ~3 s" desaparece:
> era efecto de las recargas. Se mantiene el hallazgo principal (el procesado del prompt es el
> cuello de botella); se descarta la hipótesis (a).

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

## Tanda 2 (desde 21:12): observaciones de Francisco

- **Temperatura**: no notó que el móvil se calentara en ningún momento de las pruebas.
- Durante la tanda apareció el aviso de PocketPal *"This conversation is getting long and may
  soon run out of room"* (`contextWarning`). Solo sale cuando un mismo chat acumula contexto
  cerca del límite de 2048 tokens, lo que sugiere que alguna ejecución no fue en un chat nuevo.
  Se verifica con la exportación agrupada por sesión.

## Tanda 2 (21:19–21:25): válida con una desviación

Modo avión confirmado en las capturas (icono del avión), 4 hilos, **sin recargas ni salidas a
segundo plano** entre 21:12 y 21:31 según logcat. Desviación: **las 9 ejecuciones fueron en un
único chat** (la exportación muestra una sesión con 18 mensajes). Consecuencias: cada turno solo
procesa los tokens nuevos (la caché KV reutiliza la conversación) y el contexto crece hasta el
83 % de 2048 al final, lo que frena las dos fases. Copia: `tanda2-export.json`.

| # | Hora | Prompt | `prompt_n` | Procesado (tok/s) | `predicted_n` | Generación (tok/s) | TTFT (ms) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 21:19:15 | P1 | 65 | 24,1 | 67 | 8,16 | 2 831 |
| 2 | 21:19:49 | P2 | 38 | 28,0 | 199 | 7,90 | 1 456 |
| 3 | 21:20:33 | P3 | 26 | 24,5 | 199 | 8,77 | 1 152 |
| 4 | 21:21:28 | P1 | 68 | 25,3 | 67 | 7,49 | 2 819 |
| 5 | 21:21:57 | P2 | 38 | 25,0 | 198 | 7,47 | 1 621 |
| 6 | 21:22:45 | P3 | 25 | 20,5 | 199 | 7,45 | 1 331 |
| 7 | 21:23:29 | P1 | 21 | 18,1 | 67 | 7,10 | 1 318 |
| 8 | 21:24:01 | P2 | 38 | 18,5 | 198 | 7,23 | 2 239 |
| 9 | 21:24:59 | P3 | 25 | 17,8 | 199 | 7,34 | 1 590 |

### Lectura de la tanda 2

- **Procesado del prompt: ~35–55 ms por token (18–28 tok/s), proporcional a los tokens.** Sin
  recargas desaparece el "coste fijo de 3 s" que parecía haber en la tanda 1: aquel dato estaba
  contaminado por las recargas del modelo.
- **Generación: 8,2–8,8 tok/s con el contexto corto, 7,1–7,5 tok/s con ~1 700 tokens de
  contexto.** Mediana de las 9: 7,47. Referencia con chat recién abierto: 8,16 (tanda 2, #1) y
  8,19 (tanda 1, P1).
- **TTFT de una pregunta en chat nuevo** (65 tokens de prompt): 2,8 s. Extrapolando a ~150 tokens,
  la referencia del criterio §3, serían ~4–6 s.

### Calidad de la tanda 2 (rúbrica §5, sobre el texto completo exportado)

| Prompt | Nota | Motivo |
| --- | --- | --- |
| P1 | 2 | Definición correcta y breve |
| P2 | 1 | Pasos numerados pero genéricos: no menciona ventilación ni temperatura ambiente (lo que indica F0009) y sugiere desmontar el motor sin cortar tensión |
| P3 | 1 | Menciona el riesgo, pero no manda cortar tensión ni esperar la descarga; "mantén el multímetro un minuto" no tiene sentido |

Total **4/6** (el umbral es 5/6; en el PC el mismo modelo sacó 5/6). Con el criterio de §3
("español correcto y pasos numerados cuando toca") pasa 3/3. Las tres repeticiones de cada prompt
son idénticas palabra por palabra (temperatura 0,2 y mismo prefijo en caché): repetir no aporta
información de calidad. Sin el manual, el contenido técnico es genérico, como se esperaba; el
contenido correcto tiene que venir del RAG, y los avisos de seguridad de la lógica de la app.

**Benchmark: falló.** 21:27:33, `llama_decode() failed during benchmark, n_batch=512 ret=-1`,
en 86 ms y sin resultados. Probable causa: se lanzó con el contexto del chat casi lleno (83 %) y
512 tokens más no cabían en 2048. Se repite con el modelo recién cargado.

**Después de la tanda**: al ir a Ajustes para reactivar la depuración inalámbrica, dos
auto-release y recargas (6,4 s cada una: 21:31:40.8→21:31:47.2 y 21:32:45.5→21:32:51.8), y el
proceso murió en segundo plano a las 21:33:18 (`am_proc_died`, oom_adj 900), ~28 s después de
salir. Estado final: batería 83 %, 35,5 °C; Francisco no notó calentamiento.

## Q4_0 como palanca contra el procesado lento

Qwen2.5-1.5B en Q4_0 (1,07 GB, misma fuente oficial) se descargó y se copió al móvil porque la
reorganización de pesos de llama.cpp en ARM está pensada sobre todo para Q4_0 y podría acelerar
el procesado del prompt. En el PC pierde calidad claramente (3/6 frente a 5/6, con un bucle de
repetición en P2; ver `informes/2026-09-13-calidad-pc.md`). **Queda descartado como modelo de la
demo.** Se mide su benchmark en el móvil solo como dato: si el procesado del prompt se multiplica,
la vía (Q4_0, IQ4_NL u otro modelo) merece estudio en el plan 02.

## Benchmark integrado, tanda 3 (21:45–21:57): pp 512, tg 128, 3 repeticiones

Modo avión y Wi-Fi apagado (según Francisco). Resultados leídos de la pantalla Benchmark de
PocketPal (lista completa recorrida con scroll y `uiautomator dump`) y cruzados con logcat.

| Hora fin | Modelo | Hilos (pantalla y log) | pp 512 (tok/s) | tg 128 (tok/s) | Duración | Memoria pico |
| --- | --- | --- | --- | --- | --- | --- |
| 21:49:08 | Q4_K_M | 4 | 24,62 | 6,77 | 1 min 59 s | 24,9 % de 5,3 GB |
| 21:52:06 | Q4_K_M | 4 | 24,14 | 6,66 | 2 min 2 s | 26,7 % |
| 21:54:41 | Q4_K_M | 4 | 24,86 | 6,86 | 1 min 58 s | 26,7 % |
| 21:57:09 | Q4_0 | 4 | 29,82 | 6,90 | 1 min 47 s | 26,7 % |

Cargas según logcat, todas con `n_threads=4`: 21:45:19→21:45:25 (5,9 s), 21:46:44→21:46:53
(8,9 s) y Q4_0 21:55:14→21:55:20 (5,8 s). Al reactivar la depuración inalámbrica: auto-release
y recarga 21:58:11→21:58:21 (10,2 s).

### Lectura de la tanda 3

- **El barrido de hilos no se aplicó.** Las tres pruebas del Q4_K_M corrieron sobre la misma
  carga (21:46:53). El cambio de hilos solo surte efecto al descargar el modelo ("Offload") y
  volver a cargarlo, y entre ellas no hubo recarga. Valen como **tres repeticiones con 4 hilos**,
  muy consistentes: pp 24,1–24,9 (mediana **24,6**), tg 6,7–6,9 (mediana **6,8**).
- **~25 tok/s es el techo de procesado del prompt de la CPU con 4 hilos**, no un sobrecoste de la
  app: con 512 tokens de golpe sale lo mismo que en el chat (18–28). Se descarta también la
  hipótesis (a) desde este lado.
- **Generación en el benchmark: 6,8 tok/s**, por debajo del chat (8,2) porque se mide con 512
  tokens ya en el contexto. Es la cifra realista para preguntas con contexto de RAG.
- **Q4_0: +21 % de procesado (29,8) y +2 % de generación.** La reorganización de pesos no
  multiplica la velocidad en esta CPU sin i8mm. Con su pérdida de calidad (3/6), **descartado**.
- **Memoria pico ~1,3–1,4 GB** (24,9–26,7 % de 5,3 GB).

**Pendiente**: el barrido real de hilos. llama.cpp reparte el trabajo por igual entre hilos y
con 4 hilos dos caen en los núcleos lentos A55, así que el procesado del prompt (limitado por
cálculo) puede mejorar con 6 u 8 hilos, o incluso con 2 si caen en los A78. La generación
(limitada por memoria) probablemente no.

## Barrido de hilos, tanda 4 (22:06–22:47): manejado por ADB

Francisco eligió que Claude manejara PocketPal a distancia por ADB, con el Wi-Fi encendido (el
funcionamiento sin red ya quedó demostrado en las tandas 1 a 3). Cada vuelta, con
`scratchpad/ronda.ps1`: fija los hilos en Settings, descarga el modelo ("Offload"), lo carga
eligiéndolo en Benchmark, **comprueba `n_threads=N` en logcat antes de medir**, deja 60 s de
reposo, lanza el benchmark (pp 512, tg 128, 3 repeticiones) y detecta el final leyendo el consumo
de CPU del proceso (`/proc/PID/stat`), sin tocar la interfaz mientras mide.

| Hilos | pp 512 (tok/s) | tg 128 (tok/s) | Duración | Núcleos ocupados | Carga (s) | Temperatura |
| --- | --- | --- | --- | --- | --- | --- |
| 2 | 22,82 | 8,02 | 1 min 55 s | ~3,0 | 5,9 | 33,9 → 34,0 °C |
| 4 (control) | 26,42 | 7,86 | 1 min 47 s | ~5,0 | 8,7 | 33,9 → 33,8 °C |
| **6** | **35,44** | **8,72** | **1 min 28 s** | ~6,7 | 6,1 | 33,6 → 33,3 °C |
| **6 (réplica)** | **34,14** | **9,08** | **1 min 28 s** | ~6,6 | 9,6 | 33,7 → 33,6 °C |
| 8 | 30,80 | 3,97 | 2 min 27 s | ~7,5 | 8,3 | 33,3 → 33,6 °C |

**Vuelta descartada**: una primera de 6 hilos (22:10–22:15) dio pp 27,1 / tg 1,96 en 4 min 13 s
porque el guion consultaba la interfaz con `uiautomator` cada 10 s durante el test ("could not get
idle state" en bucle), y esa consulta gasta CPU del móvil. El control de 4 hilos con el método
corregido (26,4 / 7,9) cuadra con la tanda 3 (24,6 / 6,8): el método nuevo no contamina.

### Lectura de la tanda 4

- **6 hilos es el óptimo**, y es justo el valor por defecto de PocketPal (80 % de los núcleos); el
  plan había fijado 4. Media de las dos vueltas con 6 frente a 4 hilos: procesado del prompt
  **34,8 tok/s (+32 %)** y generación **8,9 tok/s (+13 %)**. Las dos vueltas difieren menos de un 4 %.
- **La generación está limitada por la memoria**: con 2 hilos (los dos A78) ya da 8,0 tok/s, casi
  lo mismo que con 4 o 6.
- **El procesado del prompt está limitado por el cálculo**: sube con los núcleos hasta 6
  (22,8 → 26,4 → 34,8).
- **8 hilos es contraproducente**: sin un núcleo libre para Android, cada interrupción de un hilo
  hace esperar a los demás; la generación cae a la mitad (4,0) y el procesado también baja (30,8).
- **Sin problema térmico**: 33–34 °C durante todo el barrido y sin tendencia a subir, lo que
  confirma la impresión de Francisco.
- PocketPal queda configurado con 6 hilos al terminar.

## Prompts del protocolo (§5)

El diseño previsto (un chat nuevo por ejecución, tres por prompt) **no se ejecutó tal cual**. Lo
sustituyen:

- **Tandas 1 y 2**: los tres prompts en modo avión, pero en un mismo chat (solo el primer P1 de
  cada tanda va en chat nuevo). Dan la calidad (4/6) y los tiempos reales dentro del chat.
- **Tandas 3 y 4**: el benchmark pp 512 / tg 128, que da velocidades limpias y comparables.

Las tres repeticiones de cada prompt en la tanda 2 salieron idénticas palabra por palabra, así que
repetirlas en chats nuevos no habría cambiado la nota de calidad.

## Carga del modelo

| Modelo | Condición | Tiempo (s) | Cómo se midió |
| --- | --- | --- | --- |
| Qwen2.5-1.5B | primera carga tras importar (templada: fichero recién copiado, probablemente en caché) | **~9,0** | logcat `RNLlama`: `loadModel` 20:45:48.496 → `Context initialized` 20:45:57.465. A ojo pareció "un segundo o menos": la pantalla del chat aparece antes de que termine la carga |
| Qwen2.5-1.5B | recarga tras volver de segundo plano (tanda 1, 20:59:53) | 7,0 | logcat `RNLlama loadModel` → `Context initialized` |
| Qwen2.5-1.5B | recarga con más presión de memoria (tanda 1, 21:04:18) | 16,6 | ídem |
| Qwen2.5-1.5B | tras morir y reabrirse el proceso (21:10:57) | **10,2** | ídem; `n_threads=4`. Memoria después: PSS 1,39 GB, `MemAvailable` 1,19 GB |
| Qwen2.5-1.5B | cargas limpias del barrido de hilos (tanda 4, tras "Offload") | **5,9–9,6** | ídem; seis cargas: 6,4 · 8,7 · 6,1 · 8,3 · 5,9 · 9,6 |
| Qwen2.5-1.5B | tras reinicio del móvil (frío) | **no se midió** | Reiniciar corta la depuración inalámbrica y obliga a reconectar; se dejó fuera |

PocketPal no muestra el tiempo de carga; se mide por las marcas de tiempo de logcat. La carga no
usa red, así que se mide con el Wi-Fi encendido para poder leer logcat.

## Decisión según §3

Configuración evaluada: **Qwen2.5-1.5B-Instruct Q4_K_M con 6 hilos** (el óptimo del barrido),
contexto 2048, en PocketPal 1.17.3.

| Criterio §3 | Medido | GO / reservas / NO-GO | Veredicto |
| --- | --- | --- | --- |
| Generación | 8,7–9,1 tok/s (benchmark, con 512 tokens de contexto) | ≥ 8 / 5–8 / < 5 | **GO** |
| TTFT con ~150 tokens de prompt | ≈ 4,3 s (150 ÷ 34,8 tok/s de procesado); en chat, con 4 hilos: 2,8 s para 65 tokens | ≤ 2 s / 2–4 s / > 4 s | **NO-GO por 0,3 s** |
| Carga del modelo | 5,9–9,6 s limpias; 10,2 s tras reabrir la app; 16,6 s con presión de memoria; en frío, sin medir | ≤ 10 s / 10–20 s / > 20 s | **GO con reservas** |
| Español correcto y pasos numerados | 3/3 | 3/3 / 2/3 / ≤ 1/3 | **GO** (con la rúbrica fina de §5: 4/6, por debajo de 5/6) |
| Sobrevive a minimizar 60 s | PocketPal descarga el modelo al salir y el sistema cierra el proceso en menos de un minuto | Sí / — / No | No evaluable con PocketPal: es un criterio de la app propia (paso 4.9) |
| Memoria | pico ~1,4 GB; cabe en primer plano, a costa de cerrar otras apps | — | OK |
| Temperatura | 33–34 °C estables | — | OK |

### Lectura estricta del plan

El TTFT cae en la franja de NO-GO por 0,3 s. La regla 1 manda pasar al siguiente modelo, pero
Llama 3.2 1B ya suspendió la calidad en el PC (3/6), igual que Qwen2.5-0.5B (1/6) y el Q4_0 del
propio Qwen 1.5B (3/6). **Ningún candidato cumple todos los criterios en este móvil.** Por la
regla 2, eso lleva a replantear, y **la decisión es de Francisco**.

### Recomendación: GO con reservas (regla 3)

- Generación, memoria y temperatura están bien, y el modelo funciona sin red.
- El único criterio en rojo es el tiempo hasta la primera palabra. Está medido (~35 tok/s de
  procesado del prompt con 6 hilos) y es predecible, así que se puede diseñar alrededor.
- No hay alternativa mejor en este móvil: los modelos más pequeños fallan en calidad.

Condiciones para seguir, que pasan al plan 02:

1. La app propia usa **6 hilos**, no 4 como decía el plan.
2. El contexto de RAG se limita a **~250–300 tokens por pregunta** (≈ 7–9 s hasta la primera
   palabra), y el prompt de sistema se procesa una vez y se reutiliza (caché KV).
3. La respuesta se muestra en streaming, con un indicador mientras se procesa el manual.
4. Los avisos de seguridad los pone la app, no el modelo: en P3 no mandó cortar tensión.
5. Si con RAG el tiempo de respuesta no es aceptable para la demo, la salida es un móvil con i8mm,
   validado con este mismo protocolo.

### Lo que el plan pedía y no se hizo

- La carga en frío tras reiniciar el móvil.
- Los prompts en chats nuevos, tres por prompt (sustituidos por las tandas 1 y 2 y los benchmarks).
- Medir Llama 3.2 1B y Qwen2.5-0.5B en el móvil (paso 2C.9): quedaron descartados antes, por
  calidad en el PC.

**Gate G1: pendiente de la decisión de Francisco.**
