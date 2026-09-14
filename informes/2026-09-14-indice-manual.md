# Índice del manual ACS355 en el PC — 2026-09-14

Plan 02, etapa 1 (rama `feat/indice-manual`). Gate G3 al final.

PC: Windows 11, llama.cpp b10941 (`C:\tools\llama.cpp` y submódulo), uv 0.7.9.
Manual: `manuales/EN_ACS355_UM_E_A5.pdf` (440 p.).

## Índice generado (paso 1.9)

`uv run zca-indice construir` (desde `tools/`, rutas relativas a la raíz), 53 s en el PC.

| Dato | Valor |
| --- | --- |
| Chunks | **1 478** de 409 páginas (17–438 menos las que quedan vacías) |
| Tokens de Qwen por chunk | media 121,8 · mediana 134 · mínimo 3 · **máximo 170** |
| Tokens indexados | 179 950 |
| `meta.version` | `2026-09-14.1` |
| `meta.embeddings_sha256` | `7605c7f0…c5505` (el GGUF de `docs/modelos.md`) |

- El plan esperaba 1 500–2 200 chunks con ~240 000 tokens; el texto indexable real (sin portada,
  índice, contraportada ni cabeceras) son 179 950 tokens, así que 1 478 cuadra con ~122 de media.
- 16 chunks de menos de 30 tokens: restos de pantallas del panel, colas de tabla y un título suelto
  ("Declaration of conformity"). Se dejan: son 1 % y no parecen estorbar; G3 lo dirá.
- Lectura de 10 chunks al azar (semilla 20260914: ids 4, 590, 706, 717, 844, 960, 1171, 1274,
  1294, 1367): todos de una sola página y un solo tema, legibles. Las tablas de parámetros salen
  con las columnas intercaladas por pypdf (nombre partido en dos líneas, valor al final), que es
  como está el PDF; la p. 344 arrastra números sueltos de una figura.
- **Entrada 0009:** chunks 1230 (141 tok) y 1231 (133 tok), p. 362, los dos empiezan por
  "0009 MOT OVERTEMP" y juntos contienen la entrada entera. FTS5 `MATCH '"0009"'` los devuelve,
  junto con otros tres chunks que mencionan 0009 (pp. 250, 269, 354).

## Batería (paso 1.10)

`docs/bateria-manual.yaml`: 12 preguntas (4 códigos, 3 síntomas, 2 seguridad, 2 parámetros, 1
fuera del manual), cada página esperada comprobada en el texto del PDF. **Aprobada por Francisco
el 2026-09-14, antes de medir** ("aprobada", sin cambios).

## Incidencias

Escritas en el momento, en el orden en que pasaron.

1. **e5 no se convertía con el conversor tal cual (paso 1.2).** El plan suponía que
   `convert_hf_to_gguf.py` trataba `multilingual-e5-small` por la vía `XLMRobertaModel`
   (SentencePiece Unigram). Pero su `config.json` declara `architectures: ["BertModel"]` (viene de
   Multilingual-MiniLM: cuerpo BERT con el tokenizador de XLM-R), y la clase `BertModel` del
   conversor solo lee vocabularios BPE/WordPiece: `NotImplementedError: BPE pre-tokenizer was not
   recognized`. Forzar `XLMRobertaModel` tampoco vale: recorta `1 + pad_token_id` filas de las
   posiciones (lo correcto en RoBERTa, que numera desde `pad + 1`), y este modelo numera desde 0.
   Se detectó en el log del conversor: el comando en segundo plano devolvió 0 porque ese 0 era el
   de un `tail`, no el del conversor. Arreglo: `tools/convertir_e5.py`, un envoltorio que usa la
   clase `BertModel` del submódulo con el `set_vocab` de XLM-R que ya trae (el mismo que usa
   NomicBert), sin tocar el submódulo. Como el arreglo es nuestro, se añade una verificación que el
   plan no tenía: comparar los vectores de `llama-server` con los de la referencia de
   `transformers` (mean pooling) para las mismas frases. Resultado (`tools/verificar_e5.py`, Q8_0
   frente a float32): coseno mínimo **0,99984** en 7 frases, dimensión 384; cordura del plan
   sim(motor caliente, overtemperature) 0,899 > sim(motor caliente, fieldbus) 0,820. Coste: ~20 min.
2. **El PDF no tiene pie de página (paso 1.3).** El plan pedía quitar "la cabecera y el pie". Lo
   que se repite al final del texto de cada página son las cabeceras de columnas de las tablas
   (`All parameters` / `No. Name/Value Description Def/FbEq` en 68 páginas, `CODE FAULT CAUSE WHAT
   TO DO`…), que pypdf coloca al final. Eso es lo que `limpiar_cabecera` quita como "pie". Las
   páginas 1–16 (portada, índice) y 439–440 (contraportada) quedan sin capítulo y no se indexan.
3. **La entrada 0009 no cabe en un chunk de 170 tokens (paso 1.5, antes de trocear).** El paso 1.9
   pedía a la vez "máximo ≤ 170" y "la entrada 0009 entera en un solo chunk", pero la entrada
   mide **266 tokens** de Qwen (de "0009 MOT OVERTEMP" a "0010 PANEL LOSS"). En las páginas de
   fallos hay 9 bloques de más de 170 y en las de parámetros 155 de 409 (máximo 806). Se detectó
   midiendo los bloques con el tokenizador antes de escribir `trocear`. Salida elegida, sin tocar
   el presupuesto heredado del G1 (≤ 300 tokens de manual): una entrada larga se parte por frases
   y **cada trozo repite la cabecera** ("0009 MOT OVERTEMP"), de modo que todos se encuentran por
   "0009" y los dos trozos juntos (266 + cabeceras) caben en el presupuesto. La verificación de
   1.9 pasa a ser "la 0009 queda en trozos que empiezan todos por su cabecera". **Pendiente de
   que Francisco lo valide.**
4. **El test de tokens del plan comparaba textos distintos (paso 1.4).** Los 8 863 tokens de las
   pp. 351–370 se midieron sobre el texto en bruto de pypdf; el texto limpio (sin 20 cabeceras de
   página ni las cabeceras de tabla) da menos, y el test con ±2 % fallaba. El test pasa a comparar
   el texto en bruto, que es lo que demuestra que `tokenizers` cuenta igual que `llama-tokenize`.
   Comprobado con el mismo fichero en los dos: **8 860 y 8 860** (`tokenizers` 0.23.2 con el
   `tokenizer.json` fijado frente a `llama-tokenize` b10941 con el GGUF Q4_K_M, sin BOS).
5. **Hueco en las reglas de seguridad del plan, visto al redactar la batería (paso 1.10).** B09
   ("¿Cómo cambio el ventilador del variador?") lleva `riesgo: true`: el manual manda parar el
   variador y desconectarlo de la alimentación. Pero ninguno de los disparadores de `SafetyRules`
   del plan (§5, E3) salta: la pregunta no contiene "tension", "desmont", "medir"…, y el chunk de
   la p. 372 dice "WARNING! Read and follow the instructions in chapter Safety", que no está en la
   lista de los chunks. Tal como está, G4 daría 1/2 en la tarjeta de seguridad. No se cambia nada
   ahora (es de E3); queda para decidir: ampliar los disparadores (p. ej. `ventilador`, `cambi`,
   `sustitu` en la pregunta, `warning!` o `disconnect it from` en los chunks) o sustituir B09.
6. **G3 da 4/11 con la híbrida: NO-GO según §3 (paso 1.11).** Primera medida, con la batería ya
   aprobada. Antes de dar el número por bueno se descartó un fallo propio (`systematic-debugging`):
   - Vectores alineados con sus chunks: re-vectorizados 6 al azar, coseno 0,99998–1,00000.
   - **Chunks imán:** restos cortos de pantallas del panel y cabeceras sueltas atraen las
     preguntas. El id 238 (p. 97, 41 tokens: `EXIT 00:00 LOC HELP SAVE…`) sale en el top-2 de
     vectores en 6 de las 12; el id 1219 (p. 359, 15 tokens, la cabecera de la tabla de fallos) en
     3. e5 comprime las similitudes (0,77–0,84): ganan por centésimas.
   - **FTS5 encuentra el código pero no su entrada:** "0009" está en 5 chunks y la entrada
     "0009 MOT OVERTEMP" queda 3.ª por bm25, detrás de chunks que solo la mencionan (pp. 250,
     354). "2001" choca con el parámetro 2001 y la alarma queda 13.ª de 19.
   - El índice no tiene fallos: el 4/11 es de la búsqueda tal como se diseñó.
   - Hipótesis probadas **una a una**, en un script aparte, sin tocar `tools/` (recall@2 de 11):

     | Variante | FTS5 | Vectores | Híbrida | Fallos de la híbrida |
     | --- | --- | --- | --- | --- |
     | Base (lo medido) | 3 | 3 | **4** | B01 B02 B03 B04 B05 B08 B09 |
     | H1a: vectores centrados (quitar la media) | 3 | 3 | 4 | B01 B02 B03 B04 B05 B07 B08 |
     | H1b: sin chunks de menos de 40 tokens | 3 | 2 | 3 | B01 B02 B03 B04 B05 B06 B08 B09 |
     | **H2: en FTS5, primero los chunks que empiezan por el término** | 4 | 3 | **7** | B02 B05 B08 B09 |
     | H1a + H2 | 4 | 3 | 7 | B02 B05 B07 B08 |

     H1 (imanes) se descarta: quitar los imanes no mejora los vectores. El cuello de botella es que
     e5-small empareja mal la pregunta coloquial en español con el inglés del manual (página
     correcta en los puestos 12–103 para B01, B02, B04, B05, B08, B10). H2 sí: la entrada que
     define el código es la que empieza por él. Quedan: B02 (A2001 frente al parámetro 2001: los
     dos empiezan por "2001"), B05 (armario caliente), B08 (bus de continua) y B09 (ventilador, en
     el puesto 3 de vectores).
   - Aviso de método: H2 salió de mirar los fallos de esta misma batería. Es una regla general
     (no ajustada pregunta a pregunta), pero 11 preguntas son pocas: cualquier ajuste debería
     validarse con preguntas nuevas que no se hayan visto al ajustar.

## Decisión según §3 (paso 1.14)

**NO-GO: 4/11** con la búsqueda híbrida del plan (umbral NO-GO ≤ 6). FTS5 solo 3/11, vectores
solos 3/11.

Recomendación de Claude (decide Francisco): tratarlo como el replanteo que prevé §3, pero acotado
a lo que dicen los datos, sin traducir con Qwen (3–4 s más de TTFT):

1. **H2** en Python y en Kotlin (misma tabla de casos): entrada que empieza por el código primero.
2. **Glosario taller → manual** pequeño y fijo aplicado a la pregunta antes de FTS5 (p. ej.
   "ventilador" → `fan`, "bus de continua" → `"DC bus" OR "intermediate circuit"`, "armario
   caliente" → `"ambient temperature"`), determinista y sin coste de tiempo; y el prefijo de la
   pregunta ("alarma A2001" / "fallo F0009") para separar alarmas, fallos y parámetros.
3. Medir **una vez** con esta batería y con **4–6 preguntas nuevas** que escriba Francisco sin ver
   los resultados, para no dar por bueno algo ajustado a las 11.

Alternativas: aceptar H2 sola como "GO con reservas" (7/11) y seguir; o volver a la traducción de
la consulta con Qwen, que el plan descartó por el tiempo.

**Gate G3 (primera medida):** Francisco acepta la recomendación (2026-09-14: "me va tu
recomendación"), y pide que Claude escriba las preguntas nuevas y cierre los pendientes. Orden
para que el control valga: el ajuste (H2, glosario, prefijo) se escribe y se commitea **antes** de
redactar las preguntas nuevas; las páginas de las preguntas nuevas se comprueban en el texto del
PDF, sin pasar por el buscador; se mide una sola vez y el ajuste no se toca después. Límite: el
glosario y las preguntas nuevas los escribe la misma persona (Claude), así que el control es más
débil que con preguntas de Francisco.

**Pendientes de la etapa, resueltos con Francisco el 2026-09-14:**

- Entrada 0009 en dos trozos que empiezan por su cabecera (incidencia 3): se da por buena; el
  paso 1.9 del plan se actualiza.
- B09 sin tarjeta de seguridad (incidencia 5): se mantiene B09 y se amplían los disparadores de
  los chunks en el plan (E3) con `instructions in chapter safety` y `disconnect it from the ac
  power`, elegidos contando dónde aparecen en el índice: el primero en 5 chunks (pp. 49, 351,
  372, 373, 425: instalación, diagnóstico, mantenimiento y apéndice STO), el segundo solo en la
  p. 372 (cambio del ventilador). `warning!` se descartó: 38 chunks, saldría en preguntas de
  parámetros. En la pregunta no se añade nada: "cambio" también está en "cambio el sentido de
  giro" (B07) y daría falsos positivos.
- Ajuste de la búsqueda, primera medida (diagnóstico de la incidencia 6):
  - H2 se afina con los datos: la entrada 2001 de alarmas está en un chunk que empieza por la
    cabecera de la tabla (`Alarm messages… CODE ALARM CAUSE WHAT TO DO`, `2001 OVERCURRENT`), no
    por el código. La regla pasa a ser "primero los chunks con una **línea** que empieza por el
    código", y, si la pregunta habla de fallo/alarma (o prefijo F/A) o de parámetro, primero los
    del capítulo que corresponde (`Fault tracing` / `Actual signals and parameters`): separa la
    alarma 2001 del parámetro 2001.
  - De paso sale un fallo de `extraer.py`: el patrón de cabeceras de tabla no reconocía
    `CODE ALARM CAUSE WHAT TO DO` (p. 353). Corregido con su caso de test.
  - Glosario taller → manual en `docs/glosario-taller.yaml` (~45 entradas de vocabulario general),
    que viaja en el `.sqlite` (tabla `glosario`): la app lo lee de ahí y no hay que duplicarlo en
    Kotlin, solo la regla de coincidencia con la misma tabla de casos.
  - Ajuste congelado en los commits `110bb2f`–`f1a09a4`; índice regenerado como
    `meta.version = 2026-09-14.2` (1 478 chunks, glosario de 44 entradas).
  - Preguntas de control en `docs/bateria-control.yaml` (C01–C07: 2 códigos, 2 síntomas, 1
    seguridad, 1 parámetro, 1 fuera), escritas **después** de congelar el ajuste, con las páginas
    comprobadas por búsqueda de texto en el PDF y commiteadas **antes** de medir.

## G3 tras el ajuste (medida única, índice `2026-09-14.2`)

Batería principal (`uv run zca-indice evaluar --bateria docs/bateria-manual.yaml`):

| ID | Tipo | Esperadas | FTS5 | Vectores | Híbrida |
| --- | --- | --- | --- | --- | --- |
| B01 | codigo | 362 | 362, 362 ✓ | 65, 77 ✗ | 362, 65 ✓ |
| B02 | codigo | 353 | 364, 353 ✓ | 248, 97 ✗ | 364, 248 ✗ |
| B03 | codigo | 363 | 363, 255 ✓ | 97, 359 ✗ | 363, 97 ✓ |
| B04 | codigo | 361 | 361, 86 ✓ | 77, 359 ✗ | 361, 77 ✓ |
| B05 | sintoma | 360 | 365, 353 ✗ | 97, 93 ✗ | 365, 97 ✗ |
| B06 | sintoma | 359, 360 | 360, 241 ✓ | 97, 359 ✓ | 360, 97 ✓ |
| B07 | sintoma | 69, 312 | 193, 300 ✗ | 69, 78 ✓ | 193, 69 ✓ |
| B08 | seguridad | 18, 183 | 361, 183 ✓ | 57, 115 ✗ | 361, 57 ✗ |
| B09 | seguridad | 372, 373 | 215, 372 ✓ | 92, 48 ✗ | 372, 373 ✓ |
| B10 | parametro | 310 | 310, 310 ✓ | 65, 63 ✗ | 65, 310 ✓ |
| B11 | parametro | 233, 141 | 233, 67 ✓ | 233, 97 ✓ | 233, 233 ✓ |
| B12 | fuera | — | — | 97, 381 | 97, 381 |

**Recall@2: FTS5 9/11 · vectores 3/11 · híbrida 8/11** (antes 3 · 3 · 4).

Preguntas de control (`--bateria docs/bateria-control.yaml`):

| ID | Tipo | Esperadas | FTS5 | Vectores | Híbrida |
| --- | --- | --- | --- | --- | --- |
| C01 | codigo | 360 | 360, 258 ✓ | 359, 77 ✗ | 360, 359 ✓ |
| C02 | codigo | 356 | 356, 86 ✓ | 153, 356 ✓ | 356, 153 ✓ |
| C03 | sintoma | 356, 362 | 86, 136 ✗ | 362, 356 ✓ | 72, 86 ✗ |
| C04 | sintoma | 372 | 245, 359 ✗ | 93, 89 ✗ | 245, 93 ✗ |
| C05 | seguridad | 18 | — ✗ | 97, 36 ✗ | 97, 36 ✗ |
| C06 | parametro | 222, 223 | 201, 199 ✗ | 364, 222 ✓ | 201, 199 ✗ |
| C07 | fuera | — | — | 28, 95 | 28, 95 |

**Recall@2: FTS5 2/6 · vectores 3/6 · híbrida 2/6.**

Lectura:

- **Los códigos ya funcionan**, en las dos baterías: 6 de 6 con FTS5 (B01–B04, C01, C02). La
  entrada que define el código primero es una regla general y se sostiene con preguntas nuevas.
- **Lo coloquial no generaliza.** El glosario acierta cuando la pregunta usa la palabra prevista
  y falla con cualquier otra forma: "cableado del motor" (C05) no casa con "cable del motor", y
  términos genéricos como "ruido" → `noise` (C04) o "velocidad" → `speed` (C06) traen las páginas
  donde la palabra es más frecuente, no la buena. El 8/11 de la principal está inflado por haber
  escrito el glosario conociendo esas preguntas.
- **RRF empeora cuando FTS5 mete ruido:** en C03 y C06 los vectores tenían la página buena en el
  top-2 y la híbrida la perdió, porque un chunk que sale en las dos listas aunque sea abajo suma
  más que el primero de una sola.
- Los vectores (e5-small, español frente a inglés) siguen en ~3 de cada 6–11: son el eslabón débil
  para las preguntas sin código.

## Decisión según §3, segunda medida (paso 1.14)

Principal **8/11 = GO con reservas**, pero el control da **2/6**: la mejora no se sostiene con
preguntas nuevas. §3 prevé una sola ronda de ajuste y ya se ha hecho: **decide Francisco.** Las
preguntas de control ya están vistas; cualquier comparación nueva entre opciones necesita otra
tanda de preguntas que no se haya usado para elegir.

**Gate G3 (segunda medida):** Francisco elige la recomendación (2026-09-14: "tu recomendación,
continuemos"): **traducir la pregunta al inglés con Qwen antes de buscar**, medido primero en el
PC. Las dos baterías pasan a ser de desarrollo (sirven para comparar variantes); el veredicto final
se da con 5–6 preguntas nuevas escritas por Francisco sin ver resultados.

## Traducción de la consulta con Qwen (desarrollo)

Montaje, igual que lo haría el móvil: Qwen2.5-1.5B-Instruct Q4_K_M con el prompt de sistema de §6
y un turno de usuario que pide la traducción; un servidor a la vez en el puerto 8090 (primero Qwen
traduce y se guarda, luego e5 evalúa).

**Incidencia 7. Con el prompt de sistema de §6, Qwen no traduce (primera prueba).** De 18
preguntas, 12 vuelven en español (reescritas), C05 se **responde** en vez de traducirse ("Before
checking the wiring of the motor, you should disconnect the power supply.") y dos se traducen mal
(B04 "A0007 appears on the variator screen", B06 "Why does the variator discharge when the motor
stops?"). Hipótesis: "Responde siempre en español" del sistema pesa más que la petición del turno.
Coste medido: ~140–150 tokens de prompt (unos 100 del sistema, que en el móvil estarían en caché) y
10–22 de salida. Se prueban por separado: T1, el mismo sistema con una petición que declara la
excepción al idioma; T2, un prompt de sistema propio de traductor.

Resultado de las dos variantes (18 preguntas, temperatura 0,2, semilla 42; ficheros
`models/traducciones-{manual,control}-{t1,t2}.json`):

| Variante | En inglés | Fieles | Fallos | Tokens de prompt + salida |
| --- | --- | --- | --- | --- |
| Base (§6 + petición en inglés) | 6/18 | 3 | 12 en español, C05 respondida, B04 y B06 mal | ~140–150 + 10–22 |
| T1 (§6 + excepción declarada) | 9/18 | 7 | 6 "No aparece en el manual", 3 en español, C05 respondida | ~166–177 + 8–22 |
| **T2 (sistema de traductor)** | **18/18** | **16** | B05 → "thermostat"; C05 medio respondida | **~56–67 + 10–23** |

T2 es la única que traduce. En el móvil implica un segundo prefijo de sistema (el de traductor,
~40 tokens) además del de §6: o dos secuencias en la caché KV, o reprocesar uno de los dos en cada
pregunta. Coste estimado de la traducción en el A53 (35 tok/s de prompt, ~8,5 tok/s de salida):
~0,6–1,7 s de prompt + ~1,2–2,7 s de salida ≈ **2–4 s** más de TTFT, más lo que cueste el cambio de
prefijo (decisión de E2).

Búsqueda con las traducciones de T2 (vectores de "query: " + traducción; baterías de desarrollo,
17 preguntas que cuentan):

| Variante | Principal | Control | Total | Vectores solos |
| --- | --- | --- | --- | --- |
| Sin traducción, FTS5 con glosario (lo congelado) | 8/11 | 2/6 | 10/17 | 3/11 · 3/6 |
| T2 + FTS5 solo literales | 7/11 | 3/6 | 10/17 | 2/11 · 2/6 |
| T2 + FTS5 con glosario | 9/11 | 2/6 | 11/17 | 2/11 · 2/6 |
| T2 + FTS5 con palabras de la traducción | 8/11 | 3/6 | 11/17 | 2/11 · 2/6 |

**La hipótesis queda falsada:** con la pregunta en inglés, los vectores de e5-small van **peor**
(2/11, 2/6) que con la pregunta en español (3/11, 3/6). El eslabón débil no es el idioma: es la
búsqueda por vectores en sí sobre este manual (tablas con las columnas intercaladas, entradas
cortas y parecidas entre sí). La traducción daría como mucho +1 de 17 a cambio de 2–4 s de TTFT y
un segundo prefijo en la caché: **no compensa**.

Lo que sí es sólido en las dos baterías: **los códigos, 8 de 8** (B01–B04, C01, C02 y los
parámetros B10 por número), con FTS5 y la entrada que define el código primero.

Van tres rondas de arreglos (ajuste H2 + glosario, traducción con §6, traducción T2); por método
(`systematic-debugging`), no se prueba un cuarto arreglo sin replantear con Francisco.

## Decisión según §3, tercera medida (paso 1.14)

Con lo mejor sin traducción: principal 8/11, control 2/6. **Recomendación de Claude:**

1. Descartar la traducción.
2. Seguir con FTS5 + entrada que define el código + glosario, que es fiable en códigos y
   parámetros por número.
3. Antes de dar G3, **un único experimento acotado (≤ 1 h, solo PC)** con el otro lado del
   problema: un modelo de vectores más capaz, `multilingual-e5-base` (misma familia y prefijos,
   ~280 MB en Q8, en el móvil con mmap), manteniendo todo lo demás igual.
4. El veredicto se da con las 5–6 preguntas nuevas de Francisco. Si e5-base no mejora, G3 "con
   reservas" acotado: la demo promete códigos, alarmas y parámetros, y lo coloquial queda como
   ayuda con las fuentes a la vista.

**Gate G3:** pendiente de Francisco.
