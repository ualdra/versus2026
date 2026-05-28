# Páginas públicas

Las páginas públicas de Versus son rutas sin autenticación destinadas a explicar el proyecto, el equipo y el tratamiento de datos antes de iniciar sesión.

## Rutas

| Ruta | Componente | Propósito |
|---|---|---|
| `/landing` | `features/landing/pages/landing` | Entrada principal y explicación del juego. |
| `/rankings` | `features/rankings/pages/rankings` | Clasificación pública. |
| `/equipo` | `features/landing/pages/equipo` | Integrantes del proyecto, roles y stack técnico. |
| `/privacidad` | `features/landing/pages/privacidad` | Política de privacidad, datos tratados, derechos y almacenamiento local. |

Todas estas rutas se declaran en `frontend/src/app/app.routes.ts` con `loadComponent` y no usan `authGuard`.

## Equipo

La página `/equipo` usa datos estáticos en `equipo.ts`. La lista debe mantenerse alineada con el bloque `Equipo` del `README.md`.

Cada integrante define:

- `name`: nombre mostrado en la tarjeta.
- `handle`: usuario de GitHub.
- Enlace y avatar de GitHub generados desde `handle`.
- `role`: rol principal mostrado en la tarjeta.
- `focus`: áreas técnicas de contribución.
- `color` e `initials`: presentación visual de la tarjeta.

No llama al backend y no persiste estado.

## Privacidad

La página `/privacidad` es informativa y debe actualizarse cuando cambie cualquiera de estos puntos:

- Datos de cuenta o perfil tratados por frontend/backend.
- Claves persistidas en `localStorage`.
- Flujos de eliminación o anonimización de cuenta.
- Nuevos eventos WebSocket con datos personales.
- Cambios de almacenamiento de avatares o proveedor de media.

La fecha visible de última actualización vive en `privacidad.ts` (`lastUpdated`).

## Enlaces de navegación

La landing enlaza el footer a:

- Repositorio GitHub.
- `/equipo`.
- `/privacidad`.

Las páginas `/equipo` y `/privacidad` incluyen una navegación pública propia para volver a landing y alternar entre ambas páginas.

## Pruebas manuales mínimas

Antes de cerrar cambios en estas páginas:

1. Compilar el frontend con `npm run build`.
2. Abrir `/equipo` y comprobar que los avatares, enlaces de GitHub y responsive móvil se ven correctamente.
3. Abrir `/privacidad` y revisar que el contenido no se solapa en desktop ni móvil.
4. Verificar desde `/landing` que los enlaces del footer navegan a las rutas nuevas.
