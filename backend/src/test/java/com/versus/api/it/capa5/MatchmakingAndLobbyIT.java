package com.versus.api.it.capa5;

import com.versus.api.it.support.AbstractIT;
import com.versus.api.match.MatchStatus;
import com.versus.api.match.repo.MatchRepository;
import com.versus.api.match.repo.MatchmakingQueueRepository;
import com.versus.api.users.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

@DisplayName("Capa 5 — Matchmaking & Lobby (REST)")
class MatchmakingAndLobbyIT extends AbstractIT {

    @Autowired MatchmakingQueueRepository queueRepo;
    @Autowired MatchRepository matchRepo;

    // ── Matchmaking queue ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/matchmaking/queue")
    class JoinQueue {

        @Test
        @DisplayName("Entrar a la cola con BINARY_DUEL → 204, fila en BD")
        void joinQueue() {
            User u = factories.user();
            http.reqAs(u)
                    .body(Map.of("mode", "BINARY_DUEL"))
                    .post("/api/matchmaking/queue")
                    .then()
                    .statusCode(204);

            assertThat(queueRepo.findByUserId(u.getId())).isPresent();
        }

        @Test
        @DisplayName("Segundo POST con el mismo modo → idempotente, 1 fila en BD")
        void idempotente() {
            User u = factories.user();
            http.reqAs(u).body(Map.of("mode", "BINARY_DUEL")).post("/api/matchmaking/queue").then().statusCode(204);
            http.reqAs(u).body(Map.of("mode", "BINARY_DUEL")).post("/api/matchmaking/queue").then().statusCode(204);

            long count = queueRepo.findAll().stream()
                    .filter(e -> e.getUserId().equals(u.getId()))
                    .count();
            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("POST con modo distinto → reemplaza la fila anterior")
        void reemplazaModo() {
            User u = factories.user();
            http.reqAs(u).body(Map.of("mode", "BINARY_DUEL")).post("/api/matchmaking/queue").then().statusCode(204);
            http.reqAs(u).body(Map.of("mode", "PRECISION_DUEL")).post("/api/matchmaking/queue").then().statusCode(204);

            var entry = queueRepo.findByUserId(u.getId()).orElseThrow();
            assertThat(entry.getMode().name()).isEqualTo("PRECISION_DUEL");
        }

        @Test
        @DisplayName("Modo SURVIVAL (single-player) → 400 VALIDATION_ERROR")
        void modoSoloRechazado() {
            User u = factories.user();
            http.reqAs(u)
                    .body(Map.of("mode", "SURVIVAL"))
                    .post("/api/matchmaking/queue")
                    .then()
                    .statusCode(400)
                    .body("error", equalTo("VALIDATION_ERROR"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/matchmaking/queue")
    class LeaveQueue {

        @Test
        @DisplayName("Salir de la cola → 204, fila desaparece")
        void leaveQueue() {
            User u = factories.user();
            http.reqAs(u).body(Map.of("mode", "BINARY_DUEL")).post("/api/matchmaking/queue").then().statusCode(204);

            http.reqAs(u)
                    .delete("/api/matchmaking/queue")
                    .then()
                    .statusCode(204);

            assertThat(queueRepo.findByUserId(u.getId())).isEmpty();
        }
    }

    // ── Match lifecycle ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/matches")
    class CreateMatch {

        @Test
        @DisplayName("Crear partida BINARY_DUEL → 201 con matchId y roomCode de 6 chars")
        void crearPartida() {
            User u = factories.user();
            http.reqAs(u)
                    .body(Map.of("mode", "BINARY_DUEL"))
                    .post("/api/matches")
                    .then()
                    .statusCode(201)
                    .body("matchId", notNullValue())
                    .body("roomCode", hasLength(6));
        }

        @Test
        @DisplayName("Modo SURVIVAL → 400")
        void modoSoloRechazado() {
            User u = factories.user();
            http.reqAs(u)
                    .body(Map.of("mode", "SURVIVAL"))
                    .post("/api/matches")
                    .then()
                    .statusCode(400);
        }
    }

    @Nested
    @DisplayName("POST /api/matches/{id}/join")
    class JoinMatch {

        @Test
        @DisplayName("Segundo jugador se une → 200 LobbyStateDto con ambos players")
        void unirsePartida() {
            User u1 = factories.user();
            User u2 = factories.user();

            String matchId = http.reqAs(u1)
                    .body(Map.of("mode", "BINARY_DUEL"))
                    .post("/api/matches")
                    .then().statusCode(201)
                    .extract().jsonPath().getString("matchId");

            http.reqAs(u2)
                    .post("/api/matches/" + matchId + "/join")
                    .then()
                    .statusCode(200)
                    .body("players", hasSize(2));
        }

        @Test
        @DisplayName("Sala llena → 409 CONFLICT")
        void salaLlena() {
            User u1 = factories.user();
            User u2 = factories.user();
            User u3 = factories.user();

            String matchId = http.reqAs(u1)
                    .body(Map.of("mode", "BINARY_DUEL"))
                    .post("/api/matches")
                    .then().statusCode(201)
                    .extract().jsonPath().getString("matchId");

            http.reqAs(u2).post("/api/matches/" + matchId + "/join").then().statusCode(200);

            http.reqAs(u3)
                    .post("/api/matches/" + matchId + "/join")
                    .then()
                    .statusCode(409)
                    .body("error", equalTo("CONFLICT"));
        }
    }

    @Nested
    @DisplayName("GET /api/matches/{id}/lobby")
    class GetLobby {

        @Test
        @DisplayName("Devuelve snapshot consistente del lobby")
        void lobbySnapshot() {
            User u = factories.user();
            String matchId = http.reqAs(u)
                    .body(Map.of("mode", "BINARY_DUEL"))
                    .post("/api/matches")
                    .then().statusCode(201)
                    .extract().jsonPath().getString("matchId");

            http.reqAs(u)
                    .get("/api/matches/" + matchId + "/lobby")
                    .then()
                    .statusCode(200)
                    .body("matchId", equalTo(matchId))
                    .body("mode", equalTo("BINARY_DUEL"))
                    .body("players", hasSize(1));
        }
    }

    @Nested
    @DisplayName("DELETE /api/matches/{id}/abandon")
    class Abandon {

        @Test
        @DisplayName("Último jugador abandona → 204, sala FINISHED")
        void ultimoJugadorAbandona() {
            User u = factories.user();
            String matchId = http.reqAs(u)
                    .body(Map.of("mode", "BINARY_DUEL"))
                    .post("/api/matches")
                    .then().statusCode(201)
                    .extract().jsonPath().getString("matchId");

            http.reqAs(u)
                    .delete("/api/matches/" + matchId + "/abandon")
                    .then()
                    .statusCode(204);

            // Sala ya no es live — lobby returns 404
            User viewer = factories.user();
            http.reqAs(viewer)
                    .get("/api/matches/" + matchId + "/lobby")
                    .then()
                    .statusCode(404);

            var match = matchRepo.findById(UUID.fromString(matchId)).orElseThrow();
            assertThat(match.getStatus()).isEqualTo(MatchStatus.FINISHED);
        }
    }
}
