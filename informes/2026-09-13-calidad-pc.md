# Calidad de respuesta en PC — 2026-09-13

Plan 01, etapa 2B. Objetivo: separar "el modelo responde mal" de "el móvil es lento" antes de
medir en el A53. Velocidad en PC es irrelevante para la decisión; se anota solo como referencia.

## Condiciones

| Campo | Valor |
| --- | --- |
| Máquina | Windows 11, i7-11800H (16 hilos), 15,6 GB RAM |
| Motor | llama.cpp b10941, `llama-completion.exe`, build win-cpu-x64 |
| Parámetros | `n_ctx 2048`, `n_threads 8`, `temp 0.2`, `max tokens 200`, chat template del modelo (`--jinja`) |
| System prompt | El de plan 01 §5 |
| Script | `tools/calidad-pc.sh`; salidas crudas en `informes/2026-09-13-calidad-pc/` |

## Puntuación (0–2 por prompt, pasa con ≥ 5/6)

| Modelo | P1 variador | P2 F0009 + calor | P3 bus CC | Total | Pasa |
| --- | --- | --- | --- | --- | --- |
| Qwen2.5-1.5B Q4_K_M | 2 | 2 | 1 | **5/6** | **Sí** |
| Llama 3.2 1B Q4_K_M | 1 | 1 | 1 | 3/6 | No |
| Qwen2.5-0.5B Q4_K_M | 1 | 0 | 0 | 1/6 | No |

### Qwen2.5-1.5B

- P1: definición correcta y breve. Termina solo, sin llegar al tope de tokens.
- P2: cinco pasos numerados, español correcto, sin inventar la causa exacta de F0009. Genérico
  (conexiones, refrigeración, alimentación), que es lo esperable sin RAG. Cortado a 200 tokens.
- P3: pasos numerados y pide desconectar antes de medir, pero **no avisa del riesgo eléctrico
  en primer lugar** como exige el system prompt, no menciona la espera de descarga de
  condensadores y "conecta el multímetro a la entrada" es impreciso. Por eso 1 y no 2.

### Llama 3.2 1B

- P1: español correcto pero inventa una clasificación de "variadores de alta frecuencia hasta
  10 kHz / baja frecuencia hasta 100 kHz" que no existe.
- P2: pasos genéricos y repetitivos; no inventa, pero aporta poco.
- P3: sí avisa del riesgo primero (único de los tres), pero inventa "cable de 2-4 kV".

### Qwen2.5-0.5B

- P1: confunde variador con control de generación eléctrica.
- P2 y P3: bucle de repetición hasta agotar tokens. Inutilizable para esto.

## Velocidad en PC (solo referencia, no cuenta para go/no-go)

| Modelo | Carga (ms) | Generación (tok/s) |
| --- | --- | --- |
| Qwen2.5-1.5B | 1 020 – 1 470 | 15 – 18 |
| Llama 3.2 1B | ~1 000 | 18 – 19 |
| Qwen2.5-0.5B | ~600 | 38 – 41 |

## Consecuencias para el plan

1. **Qwen2.5-1.5B es el único candidato que pasa calidad.** Los fallbacks del plan (Llama 1B,
   Qwen 0.5B) sirven para medir velocidad en el A53 y tener la curva completa, pero no para la
   demo tal cual.
2. Por la regla 2 de §3 del plan 01: si el 1.5B no pasa velocidad o memoria en el A53, **no hay
   fallback válido en calidad** y toca replantear (móvil superior, otro modelo de ~1B con mejor
   español, o prompt más restrictivo). Se decide con los datos del móvil, no antes.
3. El P3 muestra que el system prompt no basta para forzar el aviso de seguridad. En el plan 02 el
   "Safety Gatekeeper" debe ser lógica de la app, no confianza en el modelo.
4. Candidatos a probar solo si el 1.5B falla en el móvil (no ahora): Qwen3-1.7B, Gemma 3 1B,
   SmolLM3. Requieren repetir esta misma prueba.
