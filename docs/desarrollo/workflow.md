# Flujo de trabajo Git

## Modelo de ramas

```
main ──────────────────────────────────────────────── producción estable
  └─ feat/#65-matchmaking ──── desarrollo de feature ─── PR → main
  └─ fix/#102-precision-score ─ bugfix ──────────────── PR → main
  └─ docs/frontend-arch ─────── solo documentación ───── PR → main
```

Cada rama parte de `main` y se integra con un Pull Request. No hay rama `develop` intermedia.

## Convención de nombres de rama

```
<tipo>/<issue>-<descripcion-corta>
```

| Tipo | Cuándo usarlo |
|---|---|
| `feat/` | Nueva funcionalidad (issue de GitHub) |
| `fix/` | Corrección de bug |
| `docs/` | Solo documentación |
| `refactor/` | Refactorización sin cambio de comportamiento |
| `test/` | Añadir o corregir tests |
| `chore/` | Cambios de infraestructura, deps, CI |

Ejemplos:
- `feat/#65-matchmaking`
- `fix/#102-precision-life-formula`
- `docs/frontend-architecture`

## Commits

Sigue [Conventional Commits](https://www.conventionalcommits.org/):

```
<tipo>(<scope>): <descripción imperativa en presente>

[cuerpo opcional — explica el POR QUÉ, no el QUÉ]

[#número-de-issue]
```

Tipos válidos: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `perf`.

Ejemplos:
```
feat(game): añade modo Precision con barra de vida continua (#59)
fix(auth): evita doble refresh cuando hay peticiones paralelas (#88)
docs(frontend): documenta arquitectura de componentes
```

## Ciclo de vida de un issue

1. El issue se abre en GitHub con etiqueta (`frontend`, `backend`, `docs`, `bug`, `sprint-3`...).
2. Se asigna a un desarrollador y se coloca en el sprint activo.
3. Se crea la rama correspondiente desde `main`.
4. Al terminar el desarrollo se abre un **Pull Request** contra `main`.
5. Al menos un revisor aprueba el PR.
6. Se hace merge con **Squash and merge** para mantener el historial limpio.
7. La rama se borra automáticamente tras el merge.

## Checklist de Pull Request

Antes de marcar el PR como listo para revisión:

- [ ] El código compila sin errores (`ng build` / `./mvnw package`)
- [ ] Los tests pasan (`ng test` / `./mvnw test`)
- [ ] No hay `console.log` ni código comentado
- [ ] Los contratos de API coinciden con `guia-de-coordinación-técnica.md`
- [ ] Si es un endpoint nuevo, está reflejado en Swagger (anotaciones `@Operation`)
- [ ] Si hay cambio de esquema de BD, se actualizó `bd-scheme.md`
- [ ] El título del PR sigue el formato Conventional Commits

## Sincronización con `main`

Mantén tu rama actualizada con `rebase`, no con `merge`:

```bash
git fetch origin
git rebase origin/main
```

Esto evita commits de merge innecesarios y facilita el squash final.

## Etiquetas de issues

| Etiqueta | Significado |
|---|---|
| `frontend` | Trabajo en Angular |
| `backend` | Trabajo en Spring Boot |
| `docs` | Solo documentación |
| `bug` | Comportamiento incorrecto confirmado |
| `sprint-3` / `sprint-4` | Sprint al que pertenece |
| `blocked` | Bloqueado por otra tarea |
| `needs-review` | Listo para revisión de código |

## Revisión de código

- Comenta sobre el código, no sobre la persona.
- Distingue entre **bloqueante** (debe resolverse antes del merge) y **sugerencia** (puede hacerse en otro issue).
- Aprueba con ✅ solo cuando el código está listo para `main`.
- Si el PR tiene conflictos, el autor los resuelve (nunca el revisor).
