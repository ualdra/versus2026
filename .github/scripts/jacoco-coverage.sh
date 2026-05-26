#!/usr/bin/env bash
# Imprime el porcentaje de cobertura de LÍNEAS a partir de un jacoco.xml.
# El informe contiene un <counter type="LINE" missed=".." covered=".."/> a
# nivel de <report> (el último del fichero). Salida: número con 1 decimal.
# Si el fichero no existe o no hay datos, imprime "0.0".

set -uo pipefail
XML="${1:-}"

if [ ! -f "$XML" ]; then
  echo "0.0"; exit 0
fi

# Última línea LINE counter = agregado del report.
read -r MISSED COVERED < <(
  grep -oP '<counter type="LINE" missed="\K[0-9]+" covered="[0-9]+"' "$XML" \
    | tail -1 \
    | sed -E 's/" covered="/ /; s/"$//'
)

MISSED="${MISSED:-0}"; COVERED="${COVERED:-0}"
TOTAL=$(( MISSED + COVERED ))
if [ "$TOTAL" -eq 0 ]; then echo "0.0"; exit 0; fi

awk -v c="$COVERED" -v t="$TOTAL" 'BEGIN { printf "%.1f", (c*100)/t }'
