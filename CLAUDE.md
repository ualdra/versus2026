# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

**Versus** is a multiplayer quiz game with 5 game modes (Survival, Precision, Binary Duel, Precision Duel, Sabotage). Questions are scraped from the web in real time. The codebase is a monorepo with three independent services: Angular frontend, Spring Boot backend, and (future) Scrapy scrapers.

## Commands

The recommended dev workflow uses VS Code Dev Containers (`frontend/.devcontainer` and `backend/.devcontainer`). Services start automatically inside the container. Manual setup:

```bash
# Copy .env from template before starting
cp .env.example .env

# Full dev stack (hot reload, pgAdmin, debug port)
docker compose -f docker-compose.yml -f docker-compose.dev.yml up

# Frontend only (inside frontend/)
npm start            # ng serve — http://localhost:4200
ng test              # vitest
ng build

# Backend only (inside backend/)
./mvnw spring-boot:run   # http://localhost:8080
./mvnw test
./mvnw package
```

Dev ports: `4200` Angular, `8080` API, `5432` PostgreSQL, `5050` pgAdmin, `5005` remote debug.

## Architecture overview

### Frontend — `frontend/src/app/`

Angular 21 SPA using standalone components (no NgModules). Folder structure:

| Folder | Contents |
|---|---|
| `features/auth/` | Login, register pages + forms |
| `features/player/` | Dashboard, profile, mode-select, lobby, result |
| `features/survival/` | Survival game page + compare-card component |
| `features/admin/` | Admin dashboard, spiders, reports, users |
| `features/landing/` | Public landing page |
| `core/` | Guards, interceptors, models, services (mostly empty — to be implemented) |
| `shared/components/` | Reusable UI components (button, card, input, badge, divider) |

All routes in `app.routes.ts` use lazy `loadComponent`. No route guards yet — implement `AuthGuard` in `core/guards/` and `AuthInterceptor` in `core/interceptors/`.

Game state (lives, score, timer) must live in a service or signal store. Components only use `@Input()` / `@Output()` — no business logic in templates.

### Backend — `backend/src/main/java/com/versus/api/`

Spring Boot 4 REST API + STOMP WebSockets. Currently only the application entry point exists — all domain/service/controller code is to be built. Uses Lombok and Spring Data JPA with PostgreSQL.

WebSocket endpoint: `ws://localhost:8080/ws`  
Client subscribes to: `/user/queue/match` (personal), `/topic/match/{matchId}` (shared room)  
Client sends to: `/app/match/answer`, `/app/match/ready`

### Database schema

Defined in `docs/bd-scheme.md`. Key tables: `users`, `questions`, `question_options`, `matches`, `match_players`, `match_rounds`, `match_answers`, `rankings`, `player_stats`, `matchmaking_queue`, `question_reports`, `spiders`, `spider_runs`. All primary keys are UUIDs.

## API contracts

The full API contract (endpoints, request/response shapes, WebSocket events, error format) is in `docs/guia-de-coordinación-técnica.md`. **This is the single source of truth between backend and frontend.** Frontend uses mock data matching the contract shape until the backend endpoint is ready.

Standard error format:
```json
{ "error": "UNAUTHORIZED", "message": "...", "status": 401 }
```

## Frontend style conventions

All design tokens are CSS custom properties with `--vs-` prefix (defined in `styles.scss`):
- `--vs-bg-base: #0d0d0f` — page background
- `--vs-accent-red/gold/blue/green/purple` — danger/points/action/correct/sabotage
- `--vs-text-primary/secondary/muted`

Fonts: **Bebas Neue** for display titles, **IBM Plex Mono** for numbers/stats, **Inter** for body text.

All shared CSS classes use `vs-` prefix (`vs-card`, `vs-btn`, `vs-input`, etc.). Animation classes: `animate-in`, `animate-correct`, `animate-wrong`, `animate-hit`. Apply via `[class.animate-wrong]="condition"` binding — never toggle in template expressions.

See `docs/style-guide.md` for full token reference and component SCSS patterns.

## Implementation order

From `docs/guia-de-coordinación-técnica.md`:
1. JWT middleware + roles (#39), DB question model (#41), login endpoint (#84), seed questions (#44)
2. Random question endpoint (#42), survival game logic (#53, #55, #56), precision game (#59, #60)
3. WebSockets backend/frontend (#63, #64), rooms + matchmaking (#65, #66)
4. Stats/ranking (#76–#78), Scrapy pipeline (#45–#50)
5. Moderation panel (#80, #81), admin panel (#82)
