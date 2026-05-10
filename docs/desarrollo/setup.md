# Configuración del entorno de desarrollo

## Opción recomendada: VS Code Dev Containers

Cada servicio tiene su propio Dev Container con todas las dependencias preinstaladas.

1. Instala [Docker Desktop](https://www.docker.com/products/docker-desktop) y la extensión **Dev Containers** de VS Code.
2. Abre la carpeta del servicio que quieres desarrollar (`frontend/` o `backend/`).
3. `Ctrl+Shift+P` → **Dev Containers: Reopen in Container**.
4. El contenedor arranca y los servicios dependientes (PostgreSQL, etc.) también.

Dentro del contenedor el servidor se inicia automáticamente con hot reload.

## Opción alternativa: setup manual

### Requisitos

| Herramienta | Versión mínima |
|---|---|
| Docker + Docker Compose | 24+ |
| Node.js | 20 LTS |
| Java JDK | 21 |
| Python | 3.11+ (solo scrapers) |

### Frontend

```bash
cd frontend
npm install
npm start        # ng serve → http://localhost:4200
```

### Backend

```bash
cd backend
./mvnw spring-boot:run   # http://localhost:8080
```

El backend necesita PostgreSQL en `localhost:5432`. Levanta solo la BD con Docker:

```bash
docker compose up postgres -d
```

### Stack completo con Docker

```bash
cp .env.example .env
docker compose -f docker-compose.yml -f docker-compose.dev.yml up
```

Ver [deployment.md](../arquitectura/deployment.md) para detalles de variables de entorno y puertos.

## Comandos frecuentes

### Frontend

| Comando | Descripción |
|---|---|
| `npm start` | Servidor de desarrollo con hot reload |
| `ng test` | Tests unitarios con Vitest |
| `ng build` | Build de producción en `dist/` |
| `ng generate component features/foo/pages/bar` | Genera componente standalone |

### Backend

| Comando | Descripción |
|---|---|
| `./mvnw spring-boot:run` | Servidor con recarga automática (devtools) |
| `./mvnw test` | Ejecuta todos los tests |
| `./mvnw package -DskipTests` | Genera el JAR sin ejecutar tests |
| `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` | Perfil dev (seed de datos) |

### Docker

| Comando | Descripción |
|---|---|
| `docker compose up` | Levanta todos los servicios |
| `docker compose up postgres -d` | Solo la base de datos, en background |
| `docker compose logs -f backend` | Logs del backend en tiempo real |
| `docker compose exec backend bash` | Shell dentro del contenedor backend |
| `docker compose down -v` | Para los servicios y borra los volúmenes |

## Swagger UI y herramientas de API

- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON**: `http://localhost:8080/v3/api-docs`
- **pgAdmin**: `http://localhost:5050` (solo con docker compose dev)

## Debug remoto del backend

El perfil dev expone el puerto `5005` para debug JDWP. En VS Code o IntelliJ añade una configuración de **Remote JVM Debug** apuntando a `localhost:5005`.

## Variables de entorno para desarrollo local (sin Docker)

Si ejecutas el backend fuera de Docker crea un fichero `backend/.env` o exporta:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/appdb
export SPRING_DATASOURCE_USERNAME=appuser
export SPRING_DATASOURCE_PASSWORD=changeme
export DDL_AUTO=create-drop
export JWT_SECRET=dev-secret-key-min-32-characters!!
export STORAGE_PROVIDER=local
```

## Solución de problemas comunes

| Problema | Causa probable | Solución |
|---|---|---|
| Puerto 4200 ocupado | Otra instancia de ng serve | `npx kill-port 4200` |
| Puerto 8080 ocupado | Otra instancia de Spring | `./mvnw spring-boot:stop` o mata el proceso |
| `DDL_AUTO` lanza error de esquema | Tablas no existen en validación | Cambia a `create-drop` en dev o aplica migraciones |
| 401 en todas las peticiones | Token expirado o mal configurado | Borra `localStorage` del navegador y vuelve a hacer login |
| CORS error desde el frontend | Backend no acepta `localhost:4200` | Comprueba `SecurityConfig` — ya está permitido por defecto en dev |
