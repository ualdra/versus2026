#!/usr/bin/env bash
# Suma los atributos de los <testsuite>/<testsuite> de uno o más ficheros JUnit
# XML (Surefire, Failsafe o vitest/jest-junit) y exporta:
#   TOTAL PASSED FAILURES ERRORS SKIPPED
#
# Uso (con `source` para heredar las variables en el shell llamante):
#   source junit-counts.sh path/to/TEST-*.xml
#
# Si no hay ficheros (la build falló antes de generar reportes), todo queda a 0.

set -uo pipefail

_sum_attr() {
  # $1 = nombre de atributo; resto = ficheros. Suma solo los <testsuite ...>
  # de primer nivel para no duplicar con <testcase>.
  local attr="$1"; shift
  grep -rh "<testsuite " "$@" 2>/dev/null \
    | grep -oP "${attr}=\"\K[0-9]+" \
    | awk '{s+=$1} END {print s+0}'
}

TOTAL=$(_sum_attr tests "$@")
FAILURES=$(_sum_attr failures "$@")
ERRORS=$(_sum_attr errors "$@")
SKIPPED=$(_sum_attr skipped "$@")
PASSED=$(( TOTAL - FAILURES - ERRORS - SKIPPED ))

export TOTAL FAILURES ERRORS SKIPPED PASSED
