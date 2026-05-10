# Achievements

Paquete raiz: `com.versus.api.achievements`

## Responsabilidad

Gestiona el catalogo de logros, el estado de desbloqueo por usuario y la notificacion de nuevos logros. La evaluacion se ejecuta al terminar una partida singleplayer desde `GameService`.

## Tablas

| Tabla | Proposito |
|---|---|
| `achievements` | Catalogo estable: `achievement_key`, nombre, descripcion, icono y categoria. |
| `user_achievements` | Desbloqueos por usuario con PK compuesta `(user_id, achievement_id)` para impedir duplicados. |

## API

| Metodo | Ruta | Auth | Respuesta |
|---|---|---|---|
| `GET` | `/api/achievements` | Bearer | `List<AchievementResponse>` |

Los logros bloqueados se devuelven con `name`, `description` e `iconKey` ocultos (`???` / `lock`) para no revelar la condicion.

## Eventos

| Evento | Canal | Payload |
|---|---|---|
| `ACHIEVEMENT_UNLOCKED` | `/user/queue/achievements` | `{ type, achievement }` |

## Catalogo inicial

- Primeros pasos: primera partida jugada, primera victoria.
- Racha: 5, 10 y 20 respuestas correctas.
- Precision: primera partida, desviacion media excelente y desviacion media menor de 5% tras 10 partidas.
- Supervivencia: primera partida, 10 rondas y partida perfecta preparada para cuando el modo lo permita.
- Multijugador: primera victoria PvP, 10 duelos y sabotaje.
- Social: 3 amigos e invitacion a partida, preparados para los modulos sociales.
- Coleccionista: jugar todos los modos.

## Frontend asociado

- Toast global al desbloquear un logro.
- Grid `Logros` en perfil con contador `desbloqueados/total`.
- Emblema del logro mas reciente sobre el avatar del topbar.
