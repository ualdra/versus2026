# Esquema de Base de Datos

En este documento se presenta el esquema de la base de datos para el sistema de preguntas y respuestas. El esquema está diseñado para soportar funcionalidades como gestión de usuarios, preguntas, partidas, rankings, estadísticas y reportes. A continuación se muestra el diagrama entidad-relación que representa las tablas principales y sus relaciones:

```mermaid
erDiagram
  users {
    uuid id PK
    string username
    string email
    string password_hash
    string avatar_url
    enum role
    timestamp created_at
  }

  questions {
    uuid id PK
    text text
    enum type
    string correct_answer
    string category
    string source_url
    timestamp scraped_at
    enum status
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
    timestamp created_at
    timestamp finished_at
  }

  match_players {
    uuid match_id FK
    uuid user_id FK
    int lives_remaining
    int score
    enum result
  }

  match_rounds {
    uuid id PK
    uuid match_id FK
    uuid question_id FK
    int round_number
  }

  match_answers {
    uuid id PK
    uuid round_id FK
    uuid user_id FK
    string answer_given
    float deviation
    int life_delta
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
    string reason
    enum status
    timestamp created_at
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

Por si no se visualiza bien, tambien se presentan las imágenes del esquema:

[Esquema de Base de Datos](img/scheme-black.svg)

[Esquema de Base de Datos](img/scheme.svg)

[Esquema de Base de Datos](img/scheme-white.svg)