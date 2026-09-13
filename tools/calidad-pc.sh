#!/usr/bin/env bash
# Prueba de calidad en PC (plan 01, etapa 2B).
# Lanza los 3 prompts fijos del protocolo (§5) contra cada modelo con llama-completion
# y guarda las salidas en informes/<fecha>-calidad-pc/<modelo>-<n>.txt
#
# Uso (Git Bash):  bash tools/calidad-pc.sh [ruta a llama.cpp]   (por defecto C:/tools/llama.cpp)

set -euo pipefail

LLAMA_DIR="${1:-/c/tools/llama.cpp}"
REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$REPO_DIR/informes/$(date +%F)-calidad-pc"
mkdir -p "$OUT_DIR"

SYS='Eres un asistente técnico industrial. Responde siempre en español, de forma breve, con pasos numerados cuando haya que hacer algo. Si hay riesgo eléctrico, avisa primero.'

PROMPTS=(
  '¿Qué es un variador de frecuencia?'
  'Un variador ABB muestra el fallo F0009 y el armario está muy caliente. ¿Qué reviso?'
  '¿Cómo mido la tensión del bus de continua del variador?'
)

MODELS=(
  qwen2.5-1.5b-instruct-q4_k_m.gguf
  Llama-3.2-1B-Instruct-Q4_K_M.gguf
  qwen2.5-0.5b-instruct-q4_k_m.gguf
)

for m in "${MODELS[@]}"; do
  tag="${m%.gguf}"
  for i in "${!PROMPTS[@]}"; do
    n=$((i + 1))
    out="$OUT_DIR/$tag-$n.txt"
    echo "== $tag / prompt $n"
    {
      echo "MODELO: $m"
      echo "PROMPT: ${PROMPTS[$i]}"
      echo "----"
      "$LLAMA_DIR/llama-completion.exe" \
        -m "$REPO_DIR/models/$m" \
        -sys "$SYS" -p "${PROMPTS[$i]}" \
        -st --jinja --simple-io --no-display-prompt \
        -n 200 -c 2048 -t 8 --temp 0.2 \
        2> "$OUT_DIR/$tag-$n.stderr.log"
    } > "$out"
    echo "----" >> "$out"
    grep -E "eval time|load time|prompt eval" "$OUT_DIR/$tag-$n.stderr.log" >> "$out" || true
  done
done

echo "Salidas en $OUT_DIR"
