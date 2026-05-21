package com.versus.api.it.capa4;

import com.versus.api.it.support.AbstractIT;
import com.versus.api.match.MatchStatus;
import com.versus.api.match.repo.MatchRepository;
import com.versus.api.users.domain.User;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

@DisplayName("Capa 4 — Precision")
class PrecisionGameIT extends AbstractIT {

    @Autowired
    MatchRepository matchRepo;

    private User player;

    @BeforeEach
    void setup() {
        for (int i = 0; i < 8; i++)
            factories.numericQuestion();
        player = factories.user();
    }

    private JsonPath startPrecision() {
        return http.reqAs(player)
                .post("/api/game/precision/start")
                .then()
                .statusCode(200)
                .body("sessionId", notNullValue())
                .body("question.id", notNullValue())
                .extract().jsonPath();
    }

    private ValidatableResponse answer(UUID sessionId, UUID questionId, BigDecimal value) {
        return http.reqAs(player)
                .body(Map.of(
                        "sessionId", sessionId.toString(),
                        "questionId", questionId.toString(),
                        "value", value))
                .post("/api/game/precision/answer")
                .then();
    }

    @Nested
    @DisplayName("Inicio")
    class Start {

        @Test
        @DisplayName("P1 — start devuelve livesRemaining=100")
        void inicioConCienVidas() {
            JsonPath jp = startPrecision();
            assertThat(jp.getString("sessionId")).isNotNull();
        }
    }

    @Nested
    @DisplayName("Respuestas")
    class Answers {

        @Test
        @DisplayName("P2 — value=100 (exacto) → lifeDelta=+5, correct=true, streak=1")
        void respuestaExacta() {
            JsonPath start = startPrecision();
            UUID sessionId = UUID.fromString(start.getString("sessionId"));
            UUID questionId = UUID.fromString(start.getString("question.id"));

            answer(sessionId, questionId, new BigDecimal("100"))
                    .statusCode(200)
                    .body("lifeDelta", equalTo(5))
                    .body("livesRemaining", equalTo(105));
        }

        @Test
        @DisplayName("P3 — value=110 (10% desviación, 2×tolerance) → lifeDelta=0, correct=false")
        void desviacionMediana() {
            JsonPath start = startPrecision();
            UUID sessionId = UUID.fromString(start.getString("sessionId"));
            UUID questionId = UUID.fromString(start.getString("question.id"));

            answer(sessionId, questionId, new BigDecimal("110"))
                    .statusCode(200)
                    .body("lifeDelta", equalTo(0))
                    .body("livesRemaining", equalTo(100));
        }

        @Test
        @DisplayName("P4 — Respuestas con value=300 hasta gameOver → match FINISHED")
        void hastaGameOver() {
            JsonPath start = startPrecision();
            UUID sessionId = UUID.fromString(start.getString("sessionId"));
            String currentQuestionId = start.getString("question.id");

            JsonPath lastResp = null;
            boolean gameOver = false;

            for (int i = 0; i < 200 && !gameOver; i++) {
                UUID questionId = UUID.fromString(currentQuestionId);
                lastResp = answer(sessionId, questionId, new BigDecimal("300"))
                        .statusCode(200)
                        .extract().jsonPath();
                gameOver = lastResp.getBoolean("gameOver");
                if (!gameOver) {
                    currentQuestionId = lastResp.getString("nextQuestion.id");
                }
            }

            assertThat(gameOver).isTrue();
            var match = matchRepo.findById(sessionId).orElseThrow();
            assertThat(match.getStatus()).isEqualTo(MatchStatus.FINISHED);
        }
    }
}
