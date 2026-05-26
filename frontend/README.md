# Frontend

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 21.2.1.

## Development server

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Building

To build the project run:

```bash
ng build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

## Running unit tests

To execute unit tests with the [Vitest](https://vitest.dev/) test runner, use the following command:

```bash
ng test
```

## Running end-to-end tests

For end-to-end (e2e) testing, run:

```bash
ng e2e
```

Angular CLI does not come with an end-to-end testing framework by default. You can choose one that suits your needs.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.

## Modos multijugador (Sprint 4 — PRs #91 #92 #93)

Los tres modos PvP comparten la misma infraestructura de WebSocket (`@stomp/stompjs` sobre SockJS) y conviven con los modos single-player ya existentes.

### Estructura

```
src/app/
├── core/
│   ├── models/duel.models.ts          ← payloads tipados + DuelEvent (discriminated union)
│   └── services/duel.service.ts        ← multicast WS, sendAnswer*, sendSabotage
├── shared/components/ui/
│   └── numeric-input/                  ← extraído de Precision single-player y compartido
└── features/
    ├── binary-duel/pages/binary-duel/
    ├── precision-duel/pages/precision-duel/
    └── sabotage/
        ├── components/sabotage-panel/
        ├── components/effect-indicator/
        └── pages/sabotage/
```

### Rutas (lazy)

| Ruta | Componente |
|---|---|
| `/play/binary-duel/:matchId` | `BinaryDuel` |
| `/play/precision-duel/:matchId` | `PrecisionDuel` |
| `/play/sabotage/:matchId` | `Sabotage` |

El componente `Lobby` navega automáticamente a la ruta del modo correspondiente cuando recibe `MATCH_START`.

### Patrón de implementación

Cada página de duelo:

1. Inyecta `DuelService.duelEvents$(matchId)` (multicast con `shareReplay`) y se suscribe en `ngOnInit`.
2. Mantiene su estado en signals locales (`selfRuntime`, `oppRuntime`, `currentQuestion`, `phase`, `secondsLeft`). No usa un store global.
3. Alinea el timer del round con `serverNow` recibido en cada `QUESTION` (autoridad sigue siendo el servidor).
4. Al recibir `MATCH_END` navega a `/play/result` con `multiplayer: true`, `outcome` y `opponent`.

Contrato completo de eventos y reglas por modo en [`docs/guia-de-coordinación-técnica.md`](../docs/guia-de-coordinación-técnica.md) y [`docs/backend/modules/duel.md`](../docs/backend/modules/duel.md).
