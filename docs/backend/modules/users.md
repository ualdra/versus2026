# Módulo: Usuarios

Paquete raíz: `com.versus.api.users`  
Estado: ✅ implementado (Sprint 1)

---

## Responsabilidad

Gestión del perfil de usuario autenticado y consulta de perfiles públicos. Este módulo **no** maneja autenticación (delegada a `auth`) ni estadísticas de juego (delegadas a `stats`).

---

## Diagrama de clases

```mermaid
classDiagram
    class UserController {
        <<RestController>>
        <<RequiresAuth>>
        +GET /api/users/me
        +PUT /api/users/me
        +PUT /api/users/me/password
        +PUT /api/users/me/avatar
        +DELETE /api/users/me
        +GET /api/users/{id}
    }

    class UserService {
        <<Service>>
        -UserRepository userRepo
        -MediaService mediaService
        +getMe(UUID userId) UserMeResponse
        +updateMe(UUID userId, UpdateMeRequest) UserMeResponse
        +changePassword(UUID userId, ChangePasswordRequest) void
        +updateAvatar(UUID userId, String avatarUrl) UserMeResponse
        +deleteMe(UUID userId) void
        +updateAvatar(UUID userId, MultipartFile) UserMeResponse
        +getPublic(UUID targetId) UserPublicResponse
    }

    class User {
        <<Entity>>
        <<Table: users>>
        +UUID id
        +String username
        +String email
        +String passwordHash
        +String avatarUrl
        +Role role
        +UserStatus status
        +Instant createdAt
        +Instant updatedAt
        +boolean isActive
    }

    class Role {
        <<Enumeration>>
        PLAYER
        MODERATOR
        ADMIN
    }

    class UserStatus {
        <<Enumeration>>
        ACTIVE
        DELETED
    }

    class UserRepository {
        <<Repository>>
        +findByEmail(String) Optional~User~
        +findByUsername(String) Optional~User~
        +existsByUsername(String) boolean
        +existsByEmail(String) boolean
    }

    class UserMeResponse {
        <<DTO>>
        +UUID id
        +String username
        +String email
        +String avatarUrl
        +String role
        +Instant createdAt
    }

    class UserPublicResponse {
        <<DTO>>
        +UUID id
        +String username
        +String avatarUrl
        +String role
        +Instant createdAt
    }

    class UpdateMeRequest {
        <<DTO>>
        +String username
        +String avatarUrl
    }

    class ChangePasswordRequest {
        <<DTO>>
        +String currentPassword
        +String newPassword
    }

    class UpdateAvatarRequest {
        <<DTO>>
        +String avatarUrl
    }

    UserController --> UserService : delega
    UserService --> UserRepository : consulta/persiste
    UserRepository --> User : gestiona
    User --> Role : usa
    User --> UserStatus : usa
    UserService ..> UserMeResponse : produce
    UserService ..> UserPublicResponse : produce
```

---

## Endpoints

| Método | Ruta | Auth | Body | Respuesta |
|---|---|---|---|---|
| `GET` | `/api/users/me` | Bearer | — | `200` `UserMeResponse` |
| `PUT` | `/api/users/me` | Bearer | `UpdateMeRequest` | `200` `UserMeResponse` |
| `PUT` | `/api/users/me/password` | Bearer | `ChangePasswordRequest` | `204` |
| `PUT` | `/api/users/me/avatar` | Bearer | JSON `UpdateAvatarRequest` | `200` `UserMeResponse` |
| `DELETE` | `/api/users/me` | Bearer | — | `204` |
| `PUT` | `/api/users/me/avatar` | Bearer | `multipart/form-data` con `file` | `200` `UserMeResponse` |
| `GET` | `/api/users/{id}` | Bearer | — | `200` `UserPublicResponse` |

### Diferencia entre `UserMeResponse` y `UserPublicResponse`

| Campo | `/me` | `/{id}` |
|---|---|---|
| `email` | ✅ | ❌ |
| `username` | ✅ | ✅ |
| `avatarUrl` | ✅ | ✅ |
| `role` | ✅ | ✅ |
| `createdAt` | ✅ | ✅ |

El email es dato privado — nunca se expone en el endpoint público.

### Errores comunes

| Situación | ErrorCode | HTTP |
|---|---|---|
| Usuario no encontrado (`/me` o `/{id}`) | `NOT_FOUND` | 404 |
| Nuevo username ya en uso | `CONFLICT` | 409 |
| Password actual incorrecta | `UNAUTHORIZED` | 401 |
| Avatar vacio, no imagen o mayor de 2MB | `VALIDATION_ERROR` | 400 |
| Body inválido | `VALIDATION_ERROR` | 400 |

---

## Entidad: `User`

```
Tabla: users
┌──────────────┬───────────────────────────────────────────────┐
│ Columna      │ Notas                                         │
├──────────────┼───────────────────────────────────────────────┤
│ id           │ UUID, PK, generado automáticamente            │
│ username     │ VARCHAR(50), UNIQUE, NOT NULL                 │
│ email        │ VARCHAR(255), UNIQUE, NOT NULL                │
│ password_hash│ VARCHAR(255), BCrypt                          │
│ avatar_url   │ TEXT, nullable                                │
│ role         │ ENUM(PLAYER, MODERATOR, ADMIN), default PLAYER│
│ status       │ ENUM(ACTIVE, DELETED), default ACTIVE         │
│ created_at   │ TIMESTAMPTZ, @PrePersist                      │
│ updated_at   │ TIMESTAMPTZ, @PreUpdate                       │
│ is_active    │ BOOLEAN, default true                         │
└──────────────┴───────────────────────────────────────────────┘
Índices: email (UNIQUE), username (UNIQUE)
```

### Lifecycle hooks JPA

- `@PrePersist`: inicializa `createdAt`, `updatedAt = now()`, `isActive = true`, `role = PLAYER`, `status = ACTIVE`
- `@PreUpdate`: actualiza `updatedAt = now()`

---

## Reglas de negocio
1. **Email visible pero no editable desde `UpdateMeRequest`**: el cambio real depende del modulo de email.
2. **Cambio de password seguro**: `PUT /api/users/me/password` exige `currentPassword` y `newPassword` de minimo 8 caracteres.
3. **Avatar predefinido**: `PUT /api/users/me/avatar` con JSON guarda una URL corta; el frontend pide confirmacion antes de persistir.
4. **Avatar propio**: `PUT /api/users/me` conserva `avatarUrl` para compatibilidad, pero `PUT /api/users/me/avatar` delega en `media` y actualiza la URL tras subir la imagen.
5. **Soft delete**: `DELETE /api/users/me` marca `status = DELETED`, `isActive = false`, anonimiza username/email/password/avatar y bloquea login/perfiles futuros.
6. **Usuarios eliminados/inactivos**: `getMe`, `getPublic`, `updateMe`, password, avatar y delete tratan cuentas `DELETED` o inactivas como `NOT_FOUND`.

---

## Cómo obtener el `userId` en un controller

Spring Security expone el sujeto del JWT como `@AuthenticationPrincipal`:

```java
@GetMapping("/me")
public UserMeResponse getMe(@AuthenticationPrincipal UUID userId) {
    return userService.getMe(userId);
}
```

El `UUID` viene del claim `sub` del access token, inyectado por `JwtAuthFilter` al crear el `UsernamePasswordAuthenticationToken`.

---

## Extensión futura

- Integrar cambio de email y password con modulo de correo para verificacion.
- Sustituir avatar en base64 por modulo multimedia/almacenamiento (#121).
- Anadir campo de XP dedicado si el producto deja de derivarlo desde `player_stats`.
