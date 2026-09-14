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
