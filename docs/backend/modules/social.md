# Módulo: Social

Paquete raíz: `com.versus.api.social`
Depende de: `users`, `match`, `achievements`, `websocket`
Estado: ✅ Implementado en issue #94.

---

## Responsabilidad

Gestiona relaciones sociales entre jugadores y permite crear una sala PvP invitando a un amigo. El módulo cubre:

- Búsqueda de usuarios activos por username.
- Solicitudes de amistad entrantes/salientes.
- Lista de amigos aceptados.
- Invitaciones a partida entre amigos.
- Eventos privados en tiempo real para solicitudes e invitaciones.

La identidad siempre sale del `@AuthenticationPrincipal UUID userId`; el cliente nunca envía el usuario emisor.

---

## Modelo de datos

| Tabla | Uso | Regla principal |
|---|---|---|
| `friendships` | Relación aceptada entre dos usuarios | Un par ordenado (`user_low_id`, `user_high_id`) solo existe una vez. |
| `friend_requests` | Solicitudes pendientes y resueltas | `requester_id` envía, `addressee_id` responde. |
| `match_invites` | Invitaciones a lobbies PvP | Solo se crean hacia usuarios que ya son amigos. |

Estados compartidos (`SocialStatus`): `PENDING`, `ACCEPTED`, `DECLINED`, `CANCELLED`.

Relación calculada para UI (`SocialRelation`): `SELF`, `NONE`, `FRIEND`, `REQUEST_SENT`, `REQUEST_RECEIVED`.

---

## Endpoints REST

Base path: `/api/social`. Todos requieren Bearer token.

| Método | Ruta | Body | Respuesta |
|---|---|---|---|
| `GET` | `/users/search?query=ra` | - | `SocialUserResponse[]` (máx. 10, mínimo 2 caracteres) |
| `GET` | `/friends` | - | `FriendResponse[]` |
| `POST` | `/friend-requests` | `{ "toUserId": "uuid" }` | `201 FriendRequestResponse` |
| `GET` | `/friend-requests/incoming` | - | `FriendRequestResponse[]` pendientes |
| `GET` | `/friend-requests/outgoing` | - | `FriendRequestResponse[]` pendientes |
| `POST` | `/friend-requests/{id}/accept` | - | `FriendRequestResponse` |
| `POST` | `/friend-requests/{id}/decline` | - | `FriendRequestResponse` |
| `DELETE` | `/friend-requests/{id}` | - | `204` (cancela una solicitud enviada) |
| `POST` | `/match-invites` | `{ "friendUserId": "uuid", "mode": "BINARY_DUEL", "matchId": "uuid?" }` | `201 MatchInviteResponse` |
| `GET` | `/match-invites/incoming` | - | `MatchInviteResponse[]` pendientes |
| `GET` | `/match-invites/outgoing` | - | `MatchInviteResponse[]` recientes |
| `POST` | `/match-invites/{id}/accept` | - | `LobbyStateDto` |
| `POST` | `/match-invites/{id}/decline` | - | `MatchInviteResponse` |

Validaciones principales:

- No se permite enviarse solicitud a uno mismo.
- No se permite duplicar amistad ni solicitud pendiente en ningún sentido.
- Las invitaciones a partida solo aceptan modos multijugador.
- Si `matchId` no se envia, el backend crea un lobby PvP nuevo y agrega al emisor.
- Si `matchId` se envia, se reutiliza una sala privada viva: el emisor debe estar dentro, el modo debe coincidir, la sala debe seguir en `WAITING` y no puede estar llena.
- No se permite duplicar una invitacion pendiente para el mismo amigo y match.
- Solo se puede invitar a usuarios que ya son amigos.
- Aceptar una invitación une al jugador al lobby y devuelve el snapshot de sala.

---

## Eventos WebSocket

Canal privado: `/user/queue/social`.

Los eventos viajan con `SocialEventEnvelope`:

```json
{ "type": "MATCH_INVITE", "payload": { "...": "..." } }
```

| Evento | Cuándo se emite | Payload |
|---|---|---|
| `FRIEND_REQUEST` | Al recibir una solicitud de amistad | `{ requestId, from: SocialUserResponse }` |
| `MATCH_INVITE` | Al recibir una invitación a partida | `{ inviteId, matchId, mode, from: SocialUserResponse }` |

El frontend los consume desde `NotificationCenterService`, respeta las preferencias de `/settings` y enlaza a `/friends`.

---

## Logros relacionados

- `social_3_friends`: se evalúa al aceptar una solicitud. Se desbloquea para cada usuario al llegar a 3 amigos.
- `social_invite`: se desbloquea para el emisor al crear una invitación a partida.

---

## Frontend

| Pieza | Fichero | Rol |
|---|---|---|
| Lobby privado | `frontend/src/app/features/player/pages/lobby/*` | Invita amigos disponibles a la sala privada actual usando `matchId`. |
| `SocialService` | `frontend/src/app/core/services/social.service.ts` | Cliente HTTP de `/api/social`. |
| Modelos TS | `frontend/src/app/core/models/social.models.ts` | Contratos usados por la página y notificaciones. |
| Página `/friends` | `frontend/src/app/features/player/pages/friends/*` | Búsqueda, solicitudes, lista de amigos e invitaciones. |
| Topbar | `frontend/src/app/shared/components/layout/topbar/*` | Entrada de navegación y notificaciones `FRIEND_REQUEST`/`MATCH_INVITE`. |

---

## Pruebas

| Fichero | Cobertura |
|---|---|
| `SocialServiceTest.java` | Solicitudes de amistad, aceptación, logros sociales, creación de invitación, rechazo de no-amigos y aceptación de invitación. |
