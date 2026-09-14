# Modelos GGUF del PoC

Descargados el 2026-09-13 en `models/` (carpeta ignorada por git). Verificar con
`sha256sum models/*.gguf` (Git Bash) o `Get-FileHash -Algorithm SHA256` (PowerShell).

| Modelo | Fichero | Bytes | SHA256 |
| --- | --- | --- | --- |
| Qwen2.5-1.5B-Instruct Q4_K_M | `qwen2.5-1.5b-instruct-q4_k_m.gguf` | 1 117 320 736 | `6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e` |
| Llama 3.2 1B Instruct Q4_K_M | `Llama-3.2-1B-Instruct-Q4_K_M.gguf` | 807 694 368 | `3f5a22426976ab26cfe84dba63c1d08391717abb1af893e10f1b2968d862dcc1` |
| Qwen2.5-0.5B-Instruct Q4_K_M | `qwen2.5-0.5b-instruct-q4_k_m.gguf` | 491 400 032 | `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db` |
| Qwen2.5-1.5B-Instruct **Q4_0** | `qwen2.5-1.5b-instruct-q4_0.gguf` | 1 066 227 232 | `dcd819ff094852c38faba6873d8ff0c9d51eadb2844539e52042ae5d647bbfdb` |

El Q4_0 se añadió tras la medición en el A53: el procesado del prompt resultó ser el cuello de
botella, y la reorganización de pesos ("weight repacking") de llama.cpp en ARM está pensada
sobre todo para Q4_0. Mismo modelo y misma fuente oficial que el Q4_K_M.

## Índice del manual (plan 02)

Generados el 2026-09-14 en `models/`. Fijados a un commit de Hugging Face para poder repetirlos.

| Fichero | Bytes | SHA256 | Origen |
| --- | --- | --- | --- |
| `multilingual-e5-small-q8_0.gguf` | 132 441 728 | `7605c7f0022cb972dbb8b05e69ff65ac0db4a3b4756f0f8a88aa8280485c5505` | `intfloat/multilingual-e5-small` @ `614241f622f53c4eeff9890bdc4f31cfecc418b3` (MIT), convertido con `tools/convertir_e5.py` (conversor del submódulo b10941) |
| `qwen2.5-tokenizer.json` | 7 031 645 | `c0382117ea329cdf097041132f6d735924b697924d6f6fc3945713e96ce87539` | `tokenizer.json` de `Qwen/Qwen2.5-1.5B-Instruct` @ `989aa7980e4cf806f80c7fef2b1adb7bc71aa306` (Apache 2.0) |

e5 no se convierte con `convert_hf_to_gguf.py` tal cual (cuerpo BERT con tokenizador de XLM-R): ver
el docstring de `tools/convertir_e5.py`. `tools/verificar_e5.py` compara el GGUF servido por
`llama-server` con la referencia de `transformers`: coseno mínimo 0,99984 en Q8_0.

## Origen

| Modelo | URL |
| --- | --- |
| Qwen2.5-1.5B | https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf |
| Llama 3.2 1B | https://huggingface.co/unsloth/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf |
| Qwen2.5-0.5B | https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf |

Ninguno requiere aceptar licencia en Hugging Face (sin gating). Licencias: Apache 2.0 (Qwen),
Llama 3.2 Community License (Llama).

## Motor de referencia en PC

`llama.cpp` build **b10941** (2026-09-13), binario `llama-b10941-bin-win-cpu-x64.zip`,
descomprimido en `C:\tools\llama.cpp\` (fuera del repo). Se usa solo para la prueba de calidad
en PC (plan 01, etapa 2B). El submódulo de la etapa 3 se fijará a este mismo tag para que PC y
móvil usen la misma versión del motor.
