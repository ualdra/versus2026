# Despliegue y entornos

## Entornos disponibles

| Entorno | Composición Docker | Objetivo |
|---|---|---|
| **Desarrollo** | `docker-compose.yml` + `docker-compose.dev.yml` | Hot reload, pgAdmin, debug remoto, seed de datos |
| **Producción** | `docker-compose.yml` + `docker-compose.prod.yml` | Imágenes optimizadas, sin herramientas de dev |

## Variables de entorno

Copia `.env.example` a `.env` antes de arrancar. Nunca subas `.env` al repositorio.

| Variable | Descripción | Valor por defecto |
|---|---|---|
| `POSTGRES_DB` | Nombre de la base de datos | `appdb` |
| `POSTGRES_USER` | Usuario PostgreSQL | `appuser` |
| `POSTGRES_PASSWORD` | Contraseña PostgreSQL | `changeme` |
| `DDL_AUTO` | Estrategia de Hibernate DDL | `create-drop` (dev) · `validate` (prod) |
| `JWT_SECRET` | Clave de firma JWT (≥32 chars) | — |
| `JWT_EXPIRATION_MS` | Vida del access token (ms) | `900000` (15 min) |
| `JWT_REFRESH_EXPIRATION_MS` | Vida del refresh token (ms) | `604800000` (7 días) |
| `STORAGE_PROVIDER` | Backend de almacenamiento | `local` · `r2` |
| `STORAGE_LOCAL_ROOT` | Ruta local para ficheros (dev) | `target/local-storage` |
| `R2_ACCOUNT_ID` | ID de cuenta Cloudflare (prod) | — |
| `R2_ACCESS_KEY` | Access key R2 (prod) | — |
| `R2_SECRET_KEY` | Secret key R2 (prod) | — |
| `R2_BUCKET` | Nombre del bucket R2 (prod) | — |
| `MEDIA_MAX_FILE_SIZE` | Tamaño máximo de upload | `10MB` |

## Puertos expuestos

| Puerto | Servicio | Solo dev |
|---|---|---|
| 4200 | Angular frontend | — |
| 8080 | Spring Boot API | — |
| 5432 | PostgreSQL | — |
| 5050 | pgAdmin | ✅ |
| 5005 | Java remote debug | ✅ |

## Arranque en desarrollo

```bash
# 1. Configurar entorno
cp .env.example .env

# 2. Levantar el stack completo
docker compose -f docker-compose.yml -f docker-compose.dev.yml up

# 3. (Opcional) Solo documentación MkDocs
docker compose -f docker-compose.yml -f docker-compose.dev.yml --profile docs up docs
```

El perfil `dev` activa `DDL_AUTO=create-drop` y `DevSeedConfig`, que inserta:
- 3 usuarios de prueba (roles: PLAYER, MODERATOR, ADMIN)
- 15 preguntas BINARY y 10 NUMERIC en estado `ACTIVE`

Credenciales de prueba:

| Email | Contraseña | Rol |
|---|---|---|
| `player@versus.com` | `player123` | PLAYER |
| `mod@versus.com` | `mod123` | MODERATOR |
| `admin@versus.com` | `admin123` | ADMIN |

## Arranque en producción

```bash
cp .env.example .env
# Editar .env: DDL_AUTO=validate, STORAGE_PROVIDER=r2, credenciales reales

docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

En producción `DDL_AUTO=validate` — Hibernate **no** crea ni modifica tablas. Las migraciones de esquema se aplican manualmente o con herramienta de migraciones antes de desplegar.

## Healthchecks

| Endpoint | Descripción |
|---|---|
| `GET /actuator/health` | Estado del backend (Spring Actuator) |
| `GET /` | Angular devuelve 200 si el build está servido |

## Swagger UI

Disponible en dev en `http://localhost:8080/swagger-ui.html`. El spec OpenAPI en `http://localhost:8080/v3/api-docs`.

## Notas de escalado

- El backend es **stateless** en cuanto a sesiones HTTP (JWT). Se puede escalar horizontalmente sin sesiones pegajosas.
- Las sesiones de juego singleplayer (`sessionId`) viven en memoria del proceso — si hay más de una instancia de backend, el `sessionId` debe apuntar a la misma instancia (sticky sessions) o migrar a Redis.
- Los canales WebSocket STOMP usan el broker en memoria de Spring. Para múltiples instancias de backend se necesita un broker externo (RabbitMQ, Redis Pub/Sub).
