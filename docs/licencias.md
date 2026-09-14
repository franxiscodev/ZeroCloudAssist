# Licencias de lo que usa la app

Lo que entra en el APK o se copia al móvil para la demo del plan 02. No es una conclusión legal:
antes de cualquier uso fuera de la demo, consultarlo.

| Pieza | Dónde | Licencia |
| --- | --- | --- |
| Qwen2.5-1.5B-Instruct (GGUF Q4_K_M) | `files/models/` del móvil | Apache 2.0 |
| multilingual-e5-small (convertido a GGUF Q8_0) | `files/models/` del móvil | MIT |
| llama.cpp (tag b10941, submódulo `third_party/llama.cpp`) | librería nativa del APK | MIT |
| androidx.sqlite `sqlite-bundled` 2.7.1 | APK | Apache 2.0 (SQLite, que empaqueta, es de dominio público) |
| Texto del manual ABB ACS355 (`EN_ACS355_UM_E_A5.pdf`) | `files/manuales/acs355.sqlite` del móvil | Propiedad de ABB |

**Manual de ABB:** su texto troceado va dentro del índice y la app lo muestra como fuente. Se usa
**solo internamente, para la demo**. Antes de enseñarlo fuera de ese marco o de distribuir el
índice, hay que consultarlo: este documento no concluye nada sobre si está permitido.

Los tests no copian fragmentos del manual: usan textos sintéticos que imitan su estructura.
