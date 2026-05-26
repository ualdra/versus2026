import { defineConfig } from 'vitest/config';

// El builder @angular/build:unit-test gestiona reporters y cobertura a través
// de las opciones declaradas en angular.json (ver target "test"). Este fichero
// queda para configuración propia de vitest que el builder sí respeta.
export default defineConfig({
  test: {},
});
