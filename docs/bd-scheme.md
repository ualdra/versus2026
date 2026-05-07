# Esquema de Base de Datos

En este documento se presenta el esquema de la base de datos para el sistema de preguntas y respuestas. El esquema está diseñado para soportar funcionalidades como gestión de usuarios, autenticación con refresh tokens, preguntas, partidas, rankings, estadísticas y reportes. A continuación se muestra el diagrama entidad-relación que representa las tablas principales y sus relaciones:

```mermaid
erDiagram
  users {
    uuid id PK
    string username UK
    string email UK
    string password_hash
    string avatar_url
    enum role
    boolean is_active
    timestamp created_at
    timestamp updated_at
  }

  refresh_tokens {
    uuid id PK
    uuid user_id FK
    string token_hash
    timestamp expires_at
    boolean revoked
    timestamp created_at
  }

  questions {
    uuid id PK
    text text
    enum type
    string category
    string source_url
    timestamp scraped_at
    enum status
    numeric correct_value
    string unit
    numeric tolerance_percent
    string text_hash UK
  }

  question_options {
    uuid id PK
    uuid question_id FK
    string text
    boolean is_correct
  }

  matches {
    uuid id PK
    enum mode
    enum status
    string room_code
    uuid owner_user_id
    timestamp created_at
    timestamp finished_at
  }

  match_players {
    uuid match_id PK,FK
    uuid user_id PK,FK
    int lives_remaining
    int score
    int current_streak
    int best_streak_in_match
    int rounds_played
    enum result
  }

  match_rounds {
    uuid id PK
    uuid match_id FK
    uuid question_id FK
    int round_number
    timestamp created_at
  }

  match_answers {
    uuid id PK
    uuid round_id FK
    uuid user_id FK
    string answer_given
    float deviation
    int life_delta
    boolean is_correct
    timestamp answered_at
  }

  rankings {
    uuid id PK
    uuid user_id FK
    enum mode
    int score
    int position
    timestamp updated_at
  }

  player_stats {
    uuid id PK
    uuid user_id FK
    enum mode
    int games_played
    int games_won
    float avg_deviation
    int best_streak
    int current_streak
  }

  matchmaking_queue {
    uuid id PK
    uuid user_id FK
    enum mode
    timestamp entered_at
  }

  question_reports {
    uuid id PK
    uuid question_id FK
    uuid reported_by FK
    enum reason
    string comment
    enum status
    timestamp created_at
    uuid resolved_by FK
    timestamp resolved_at
    enum action
  }

  spiders {
    uuid id PK
    string name
    string target_url
    timestamp last_run_at
    enum status
    uuid managed_by FK
  }

  spider_runs {
    uuid id PK
    uuid spider_id FK
    timestamp started_at
    timestamp finished_at
    int questions_inserted
    int errors
  }

  users ||--o{ refresh_tokens : "tiene"
  users ||--o{ match_players : "juega en"
  matches ||--o{ match_players : "tiene"
  matches ||--o{ match_rounds : "contiene"
  questions ||--o{ match_rounds : "aparece en"
  questions ||--o{ question_options : "tiene"
  match_rounds ||--o{ match_answers : "genera"
  users ||--o{ match_answers : "responde"
  users ||--o{ rankings : "tiene"
  users ||--o{ player_stats : "acumula"
  users ||--o{ matchmaking_queue : "espera en"
  users ||--o{ question_reports : "reporta"
  questions ||--o{ question_reports : "es reportada"
  users ||--o{ spiders : "gestiona"
  spiders ||--o{ spider_runs : "ejecuta"
```

## Cambios respecto a la versión inicial (Sprint 1)

| Cambio | Motivo |
|---|---|
| `users.is_active`, `users.updated_at` | Soft-deactivation de cuentas y auditoría. |
| `users.email` y `users.username` con UNIQUE | Garantiza unicidad para login y registro. |
| Nueva tabla `refresh_tokens` | JWT con rotación: hash + expiración + flag de revocado. |
| `questions.correct_value` (NUMERIC) sustituye a `correct_answer` (string) | Tipado correcto para modo PRECISION. |
| `questions.unit` (string) | Unidad de la respuesta numérica (ej. "millones", "%", "goles"). |
| `questions.tolerance_percent` (NUMERIC, default 5) | Margen de error aceptado en PRECISION. |
| `match_players` con clave compuesta `(match_id, user_id)` | Modelado correcto de la relación N:M. |
| `match_players.current_streak`, `best_streak_in_match`, `rounds_played` | Estado de partida singleplayer (Survival/Precision). |
| `match_answers.is_correct` | Permite filtros y stats sin recalcular desviación. |
| `matches.owner_user_id` | Identifica al dueño/host (singleplayer = único jugador). |


## Cambios introducidos en issue #100 (Moderación)

| Cambio | Motivo |
|---|---|
| `question_reports.reason` cambia de `string` a `enum` | Valores controlados: `WRONG_ANSWER`, `OUTDATED`, `OFFENSIVE`, `OTHER`. |
| `question_reports.comment TEXT` (nuevo) | Comentario libre opcional del jugador al reportar. |
| `question_reports.resolved_by UUID FK(users)` (nuevo) | Quién resolvió el reporte. |
| `question_reports.resolved_at TIMESTAMP` (nuevo) | Cuándo se resolvió. |
| `question_reports.action enum` (nuevo) | Acción tomada al resolver: `DISMISS`, `EDIT_QUESTION`, `DELETE_QUESTION`. |
| `questions.status` añade valor `FLAGGED` | Pregunta auto-flaggeada al acumular 5 reportes PENDING. Deja de servirse en partidas. |


## Cambios introducidos en issue #97 (Pipeline Scrapy)

| Cambio | Motivo |
|---|---|
| `questions.text_hash VARCHAR(64) UNIQUE` | Deduplicación idempotente en el pipeline Scrapy: SHA-256 del texto de la pregunta. Permite ejecutar el mismo spider varias veces sin crear duplicados. |

## Índices

- `users(email)` UNIQUE, `users(username)` UNIQUE.
- `questions(status, type, category)` compuesto (filtro frecuente para `/api/questions/random`).
- `questions(text_hash)` UNIQUE (deduplicación del pipeline Scrapy).
- `rankings(mode, score DESC)`.
- `matchmaking_queue(mode, entered_at)`.
- `match_rounds(match_id)`, `match_answers(round_id)`, `match_answers(user_id)`.
- `refresh_tokens(user_id)`, `refresh_tokens(token_hash)`.

Por si no se visualiza bien, también se presentan las imágenes del esquema:

[Esquema de Base de Datos](img/scheme-black.svg)

[Esquema de Base de Datos](img/scheme.svg)

[Esquema de Base de Datos](img/scheme-white.svg)
