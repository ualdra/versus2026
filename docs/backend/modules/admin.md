# Módulo: Administración

Paquete raíz: `com.versus.api.admin`

## Responsabilidad

Expone los endpoints exclusivos para administradores: gestión de usuarios (listado, cambio de rol, activación/suspensión), KPIs de plataforma, distribución de partidas por modo de juego y registro de actividad reciente.

Todos los endpoints cuelgan de `/api/admin` y exigen el rol `ADMIN` (`@PreAuthorize("hasRole('ADMIN')")`).

## API

| Método | Ruta | Auth | Respuesta |
|---|---|---|---|
| `GET` | `/api/admin/users` | Bearer (ADMIN) | `AdminUserPageResponse` — paginado y filtrable por `search`, `role`, `active` |
| `PUT` | `/api/admin/users/{id}/role` | Bearer (ADMIN) | `AdminUserResponse` — cambia el rol del usuario |
| `PUT` | `/api/admin/users/{id}/status` | Bearer (ADMIN) | `AdminUserResponse` — activa o suspende la cuenta |
| `GET` | `/api/admin/stats` | Bearer (ADMIN) | `AdminStatsResponse` — KPIs de plataforma |
| `GET` | `/api/admin/stats/modes` | Bearer (ADMIN) | `List<ModeDistributionResponse>` — distribución de partidas terminadas por modo |
| `GET` | `/api/admin/logs` | Bearer (ADMIN) | `List<AdminLogResponse>` — actividad reciente (`limit` máx. 100) |

## Reglas de negocio

- Un administrador no puede cambiarse su propio rol ni suspender su propia cuenta (`400`).
- `GET /api/admin/users` admite paginación (`page`, `size`) y filtros opcionales por texto, rol y estado activo.
- `GET /api/admin/logs` limita el número de entradas a 100 como máximo, aunque se solicite un valor mayor.

## Frontend asociado

Panel de administración en `features/admin/` (dashboard, usuarios, reportes, spiders).
