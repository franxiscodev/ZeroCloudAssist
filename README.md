# ZeroCloudAssist

Asistente técnico industrial **100 % offline** para móvil. El técnico pregunta en español sobre
un manual de equipo (en inglés) y recibe pasos concretos con cita de página. Sin nube, sin
coste por consulta, sin que la documentación salga del dispositivo.

**Estado:** prueba de concepto. Objetivo inmediato: validar que un LLM pequeño corre a
velocidad útil en un Samsung Galaxy A53 (6 GB RAM) antes de construir nada encima.

## Plan vigente

- [plan/01-zca-poc-hola-mundo.md](plan/01-zca-poc-hola-mundo.md): entorno Android, medición
  de modelos en el móvil y app mínima en Kotlin con llama.cpp. Criterios go/no-go incluidos.

Documentos de origen: [plan/zca-description.md](plan/zca-description.md),
[plan/zca-plan.md](plan/zca-plan.md), [plan/zca-config.md](plan/zca-config.md).

## Estructura

```
plan/        planes numerados y documentos de origen
manuales/    PDFs del equipo piloto (ABB ACS355), ignorados por git
models/      GGUF descargados, ignorados por git
informes/    mediciones y decisiones
docs/        entorno, versiones, notas técnicas
android/     app Android (etapa 4 del plan 01)
third_party/ llama.cpp como submódulo (etapa 3 del plan 01)
tools/       scripts Python con uv (plan 02)
```

## Modelos (descarga manual, ver plan 01 §2A)

| Modelo | Fichero | Tamaño |
| --- | --- | --- |
| Qwen2.5-1.5B-Instruct Q4_K_M | `qwen2.5-1.5b-instruct-q4_k_m.gguf` | 1,12 GB |
| Llama 3.2 1B Instruct Q4_K_M | `Llama-3.2-1B-Instruct-Q4_K_M.gguf` | 808 MB |
| Qwen2.5-0.5B-Instruct Q4_K_M | `qwen2.5-0.5b-instruct-q4_k_m.gguf` | 491 MB |
