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

**Gate G3:** pendiente de Francisco.
