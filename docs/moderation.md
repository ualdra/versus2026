# Sistema de moderación — Issue #100

> Documentación de la implementación del sistema de reportes de preguntas y el panel de moderación.
> Cubre: flujo completo de reporte, enums de motivo y acción, auto-flagging, endpoints con contratos, y estructura de respuesta.

---

## Visión general

```
Jugador (PLAYER)
    │  POST /api/questions/{id}/report
    ▼
ModerationService.report()
    ├── Valida que la pregunta existe y no está INACTIVE
    ├── Comprueba que el jugador no tiene ya un reporte PENDING para esa pregunta
    ├── Guarda QuestionReport con status = PENDING
    └── Si pendingCount >= 5 y question.status == ACTIVE → question.status = FLAGGED

Moderador (MODERATOR / ADMIN)
    │  GET  /api/moderation/reports?status=PENDING
    │  PUT  /api/moderation/reports/{id}/resolve
    ▼
ModerationService.resolve()
    ├── DISMISS        → report.status = DISMISSED
    ├── EDIT_QUESTION  → report.status = RESOLVED  (moderador edita manualmente)
    └── DELETE_QUESTION → report.status = RESOLVED + question.status = INACTIVE
```

---

## Enums

### `ReportReason` — Motivo del reporte

| Valor | Descripción |
|---|---|
| `WRONG_ANSWER` | La respuesta correcta es errónea |
| `OUTDATED` | El dato está desactualizado |
| `OFFENSIVE` | Contenido ofensivo o inapropiado |
| `OTHER` | Otro motivo (se puede añadir comentario libre) |

### `ResolveAction` — Acción al resolver

| Valor | Efecto en la pregunta | Estado del reporte |
|---|---|---|
| `DISMISS` | Ninguno | `DISMISSED` |
| `EDIT_QUESTION` | Ninguno (el moderador edita aparte) | `RESOLVED` |
| `DELETE_QUESTION` | `question.status = INACTIVE` (soft delete) | `RESOLVED` |

### `ReportStatus` — Estado del reporte (preexistente)

| Valor | Descripción |
|---|---|
| `PENDING` | Esperando revisión |
| `RESOLVED` | Resuelto con acción |
| `DISMISSED` | Descartado por el moderador |

---

## Auto-flagging

Cuando se registra un reporte, el servicio cuenta cuántos reportes `PENDING` tiene la pregunta. Si ese conteo alcanza o supera **5** y la pregunta está `ACTIVE`, su estado cambia automáticamente a `FLAGGED`.

```
REPORT_FLAG_THRESHOLD = 5
```

- Una pregunta `FLAGGED` no aparece en rotación de juego (el endpoint `/api/questions/random` solo devuelve `ACTIVE`).
- El moderador la revisa y decide: activarla de nuevo (tras editar), o eliminarla.
- Las preguntas ya en estado `INACTIVE` no se pueden reportar (el endpoint devuelve 404).

---

## Endpoints

### Reportar pregunta (PLAYER autenticado)

```
POST /api/questions/{id}/report
Authorization: Bearer <token>
```

**Request:**
```json
{
  "reason": "WRONG_ANSWER",
  "comment": "La respuesta correcta debería ser 640 millones, no 850 millones."
}
```

> `comment` es opcional. `reason` es obligatorio (debe ser uno de los valores de `ReportReason`).

**Response 201:**
```json
{
  "id": "uuid-reporte",
  "questionId": "uuid-pregunta",
  "questionText": "¿Cuántos seguidores tiene Cristiano en Instagram?",
  "questionType": "NUMERIC",
  "questionCategory": "football",
  "reason": "WRONG_ANSWER",
  "comment": "La respuesta correcta debería ser 640 millones, no 850 millones.",
  "status": "PENDING",
  "createdAt": "2026-05-07T10:00:00Z",
  "resolvedBy": null,
  "resolvedAt": null,
  "action": null
}
```

**Errores posibles:**

| Código | Cuándo |
|---|---|
| `404` | La pregunta no existe o está `INACTIVE` |
| `409` | El usuario ya tiene un reporte `PENDING` para esa pregunta |

---

### Listar reportes (MODERATOR / ADMIN)

```
GET /api/moderation/reports
GET /api/moderation/reports?status=PENDING
GET /api/moderation/reports?status=PENDING&page=0&size=20
Authorization: Bearer <token>
```

Devuelve una página de `ReportResponse` ordenada por `createdAt` descendente.

El parámetro `status` es opcional. Si se omite, devuelve todos los reportes.

**Response 200 (Page):**
```json
{
  "content": [
    {
      "id": "uuid-reporte",
      "questionId": "uuid-pregunta",
      "questionText": "¿Cuántos seguidores tiene Cristiano en Instagram?",
      "questionType": "NUMERIC",
      "questionCategory": "football",
      "reason": "WRONG_ANSWER",
      "comment": "...",
      "status": "PENDING",
      "createdAt": "2026-05-07T10:00:00Z",
      "resolvedBy": null,
      "resolvedAt": null,
      "action": null
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0
}
```

---

### Resolver reporte (MODERATOR / ADMIN)

```
PUT /api/moderation/reports/{id}/resolve
Authorization: Bearer <token>
```

**Request:**
```json
{
  "action": "DELETE_QUESTION"
}
```

> `action` debe ser uno de los valores de `ResolveAction`.

**Response 200:**
```json
{
  "id": "uuid-reporte",
  "questionId": "uuid-pregunta",
  "questionText": "¿Cuántos seguidores tiene Cristiano en Instagram?",
  "questionType": "NUMERIC",
  "questionCategory": "football",
  "reason": "WRONG_ANSWER",
  "comment": "...",
  "status": "RESOLVED",
  "createdAt": "2026-05-07T10:00:00Z",
  "resolvedBy": "uuid-moderador",
  "resolvedAt": "2026-05-07T11:00:00Z",
  "action": "DELETE_QUESTION"
}
```

**Errores posibles:**

| Código | Cuándo |
|---|---|
| `404` | El reporte no existe |
| `409` | El reporte ya está `RESOLVED` o `DISMISSED` |

---

## Control de acceso

| Endpoint | Rol mínimo |
|---|---|
| `POST /api/questions/{id}/report` | `PLAYER` (cualquier usuario autenticado) |
| `GET /api/moderation/reports` | `MODERATOR` |
| `PUT /api/moderation/reports/{id}/resolve` | `MODERATOR` |

El acceso al panel de moderación se protege con `@PreAuthorize("hasAnyRole('MODERATOR','ADMIN')")` a nivel de clase en `ModerationController`.

---

## Cambios al esquema DB introducidos por esta issue

| Tabla | Cambio | Motivo |
|---|---|---|
| `question_reports` | `reason` cambia de `string` a `enum(ReportReason)` | Valores controlados: `WRONG_ANSWER`, `OUTDATED`, `OFFENSIVE`, `OTHER` |
| `question_reports` | Nuevo campo `comment TEXT` | Comentario libre opcional del jugador |
| `question_reports` | Nuevo campo `resolved_by UUID FK(users)` | Quién resolvió el reporte |
| `question_reports` | Nuevo campo `resolved_at TIMESTAMP` | Cuándo se resolvió |
| `question_reports` | Nuevo campo `action enum(ResolveAction)` | Acción tomada: `DISMISS`, `EDIT_QUESTION`, `DELETE_QUESTION` |
| `questions` | Nuevo valor de `status`: `FLAGGED` | Pregunta auto-flaggeada por acumulación de reportes |

Ver esquema actualizado en [`docs/bd-scheme.md`](bd-scheme.md).
