#!/usr/bin/env bash
# Imprime el porcentaje de cobertura de LÍNEAS a partir de un lcov.info.
# Suma LF (líneas encontradas) y LH (líneas cubiertas) de todos los ficheros.
# Salida: número con 1 decimal. Si no hay datos, imprime "0.0".

set -uo pipefail
LCOV="${1:-}"

if [ ! -f "$LCOV" ]; then
  echo "0.0"; exit 0
fi

LF=$(grep -oP '^LF:\K[0-9]+' "$LCOV" | awk '{s+=$1} END {print s+0}')
LH=$(grep -oP '^LH:\K[0-9]+' "$LCOV" | awk '{s+=$1} END {print s+0}')

if [ "${LF:-0}" -eq 0 ]; then echo "0.0"; exit 0; fi

awk -v h="$LH" -v f="$LF" 'BEGIN { printf "%.1f", (h*100)/f }'
