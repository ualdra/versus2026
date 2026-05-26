#!/bin/bash
set -e

mkdir -p output

SPIDERS=(
  "bota_de_oro"
  "goleadores"
  "goleadores_historia"
  "palmares_clubes"
  "rrss"
  "trofeo_zarra"
  "worldometers"
  "boxoffice_mojo_worldwide"
)

for spider in "${SPIDERS[@]}"; do
  echo "==> Ejecutando spider: $spider"
  scrapy crawl "$spider" -O "output/${spider}.json" -s LOG_LEVEL=WARNING -s ITEM_PIPELINES={}
  echo "    Guardado en output/${spider}.json"
done

echo ""
echo "Todos los spiders completados."
