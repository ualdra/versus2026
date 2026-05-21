# Versus — Documentación


**Versus** es un juego de preguntas multijugador con 5 modos de juego: Survival, Precision, Binary Duel, Precision Duel y Sabotage. Las preguntas se obtienen en tiempo real mediante scrapers propios.

## Servicios

| Servicio | Tecnología | Puerto (dev) |
|---|---|---|
| Frontend | Angular 21 | 4200 |
| Backend | Spring Boot 4 | 8080 |
| Base de datos | PostgreSQL 18 | 5432 |
| pgAdmin | pgAdmin 4 | 5050 |
| Scraper | Scrapy (Python) | — |

## Documentación por área

<div class="grid cards" markdown>

-   :material-layers: **Arquitectura**

    Visión general del sistema, flujos de petición y guía de despliegue.

    [:octicons-arrow-right-24: Overview](arquitectura/overview.md) · [Despliegue](arquitectura/deployment.md)

-   :material-wrench: **Desarrollo**

    Configuración del entorno, comandos y flujo de trabajo Git.

    [:octicons-arrow-right-24: Setup](desarrollo/setup.md) · [Workflow](desarrollo/workflow.md)

-   :material-server: **Backend**

    Módulos, entidades, endpoints y diagramas de clases del API REST.

    [:octicons-arrow-right-24: Ver backend](backend/README.md)

-   :material-language-typescript: **Frontend**

    Arquitectura Angular, gestión de estado con signals y guía de testing.

    [:octicons-arrow-right-24: Arquitectura](frontend/arquitectura.md) · [Testing](frontend/testing.md)

-   :material-database: **Base de datos**

    Esquema ER completo con todas las tablas, índices y relaciones.

    [:octicons-arrow-right-24: Ver esquema](bd-scheme.md)

-   :material-api: **Guía de coordinación técnica**

    Contratos de API entre frontend y backend. Fuente de verdad.

    [:octicons-arrow-right-24: Ver contratos](guia-de-coordinación-técnica.md)

-   :material-palette: **Style guide**

    Tokens de diseño, componentes y convenciones de CSS del frontend.

    [:octicons-arrow-right-24: Ver style guide](style-guide.md)

-   :material-test-tube: **QA**

    Estrategia de testing y plan de pruebas por módulo.

    [:octicons-arrow-right-24: Estrategia](qa/estrategia.md) · [Plan de pruebas](qa/plan-de-pruebas.md)


-   :material-spider: **Scraping**

    Spiders de Scrapy, pipeline de preguntas, deduplicación y guías para añadir nuevas fuentes.

    [:octicons-arrow-right-24: Índice](scraping/README.md) · [Spiders](scraping/spiders.md) · [Pipeline](scraping/pipeline.md)

</div>

## Roadmap de sprints

| Sprint | Contenido | Estado |
|---|---|---|
| 1-2 | Auth, usuarios, preguntas, juego singleplayer, stats | ✅ |
| 3 | WebSockets, matchmaking, modos PvP | 🚧 |
| 4 | Historial, ranking, moderación, admin, scraping | 🚧 |

## Funcionalidades recientes

- `/settings`: pagina centralizada para editar username, password, avatar, notificaciones, audio y eliminar cuenta.
- Avatar: galeria predefinida con confirmacion, upload PNG/JPEG con crop basico y limite de 2MB.
- Topbar: muestra username/avatar reales y XP derivado de `player_stats` hasta tener un campo `xp` dedicado.
- Cuenta: soft delete con `status=DELETED`, `is_active=false` y anonimizacion de datos.
- Logros: catalogo inicial, desbloqueo al terminar partida, toast global, grid en perfil y emblema reciente en el avatar.

## Arranque rápido

```bash
# Copiar variables de entorno
cp .env.example .env

# Stack completo con hot reload
docker compose -f docker-compose.yml -f docker-compose.dev.yml up

# Solo la documentación
docker compose -f docker-compose.yml -f docker-compose.dev.yml --profile docs up docs
```
