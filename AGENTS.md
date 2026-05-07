# AGENTS.md

Guia para agentes que trabajen en este repositorio. Mantiene las mismas normas base que `CLAUDE.md`, adaptadas a Codex.

## Proyecto

**Versus** es un juego multijugador de quiz con 5 modos: Survival, Precision, Binary Duel, Precision Duel y Sabotage. Es un monorepo con:

- `frontend/`: Angular 21 con standalone components.
- `backend/`: Spring Boot 4 REST API + STOMP WebSockets.
- `scraper/`: scrapers futuros con Scrapy.

## Comandos habituales

El flujo recomendado usa VS Code Dev Containers en `frontend/.devcontainer` y `backend/.devcontainer`. Para ejecucion manual:

```bash
# Copiar variables antes de levantar servicios
cp .env.example .env

# Stack completo de desarrollo
docker compose -f docker-compose.yml -f docker-compose.dev.yml up

# Frontend, dentro de frontend/
npm start
ng test
ng build

# Backend, dentro de backend/
./mvnw spring-boot:run
./mvnw test
./mvnw package
```

Puertos de desarrollo:

- `4200`: Angular.
- `8080`: API.
- `5432`: PostgreSQL.
- `5050`: pgAdmin.
- `5005`: debug remoto.

## Arquitectura

### Frontend

Ruta principal: `frontend/src/app/`.

- Usar Angular standalone components, sin NgModules.
- Mantener rutas lazy con `loadComponent` en `app.routes.ts`.
- Mantener guards en `core/guards/` e interceptors en `core/interceptors/`.
- Mantener modelos y servicios compartidos en `core/models/` y `core/services/`.
- Mantener componentes reutilizables en `shared/components/`.
- La logica de negocio y estado de juego debe vivir en servicios o signals, no en templates.

Estructura relevante:

- `features/auth/`: login y registro.
- `features/player/`: dashboard, perfil, seleccion de modo, lobby y resultado.
- `features/survival/`: pantalla de survival y componentes asociados.
- `features/admin/`: dashboard, spiders, reportes y usuarios.
- `features/landing/`: landing publica.

### Backend

Ruta principal: `backend/src/main/java/com/versus/api/`.

- Spring Boot 4 con JPA, PostgreSQL y Lombok.
- REST API protegida con JWT.
- STOMP WebSockets en `ws://localhost:8080/ws`.
- Suscripciones cliente:
  - `/user/queue/match`
  - `/topic/match/{matchId}`
- Envios cliente:
  - `/app/match/answer`
  - `/app/match/ready`

### Base de datos

El esquema esta documentado en `docs/bd-scheme.md`. Las tablas principales son `users`, `questions`, `question_options`, `matches`, `match_players`, `match_rounds`, `match_answers`, `rankings`, `player_stats`, `matchmaking_queue`, `question_reports`, `spiders` y `spider_runs`. Las claves primarias son UUID.

## Contratos API

La fuente de verdad de contratos entre frontend y backend es `docs/guia-de-coordinacion-tecnica.md` si existe con ese nombre normalizado, o el archivo equivalente en `docs/` con el titulo "guia de coordinacion tecnica".

Formato de error estandar:

```json
{ "error": "UNAUTHORIZED", "message": "...", "status": 401 }
```

Si un endpoint backend aun no existe, el frontend puede usar mocks, pero deben respetar el shape del contrato.

## Convenciones frontend

- Tokens CSS en `styles.scss` con prefijo `--vs-`.
- Clases compartidas con prefijo `vs-`.
- Fuentes:
  - `Bebas Neue` para titulos display.
  - `IBM Plex Mono` para numeros y estadisticas.
  - `Inter` para texto general.
- Usar patrones de `docs/style-guide.md` cuando haya dudas de estilo.
- Aplicar animaciones con bindings de clase, por ejemplo `[class.animate-wrong]="condition"`.
- Evitar meter logica compleja en templates.

## Normas de trabajo

- Leer el codigo existente antes de cambiarlo.
- Preferir patrones ya presentes en el repo frente a abstracciones nuevas.
- Mantener cambios acotados a la issue o tarea.
- No modificar archivos no relacionados.
- No revertir cambios del usuario.
- Anadir tests cuando el cambio altere comportamiento observable.
- Ejecutar la verificacion mas cercana al cambio:
  - Frontend: tests/build de Angular cuando aplique.
  - Backend: tests Maven concretos o suite completa segun alcance.
- Documentar cualquier supuesto importante si no se puede verificar en codigo.

## Orden de implementacion recomendado

Seguir el orden definido en la guia tecnica:

1. JWT middleware + roles, modelo de preguntas, login endpoint y seed questions.
2. Endpoint de pregunta aleatoria, survival y precision.
3. WebSockets, rooms y matchmaking.
4. Stats/ranking y pipeline Scrapy.
5. Panel de moderacion y panel admin.
