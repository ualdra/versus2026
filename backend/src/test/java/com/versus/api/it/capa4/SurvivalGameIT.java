package com.versus.api.it.capa4;

import com.versus.api.it.support.AbstractIT;
import com.versus.api.match.MatchStatus;
import com.versus.api.match.repo.MatchRepository;
import com.versus.api.users.domain.User;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

@DisplayName("Capa 4 — Survival")
class SurvivalGameIT extends AbstractIT {

    @Autowired
    MatchRepository matchRepo;

    private User player;

    @BeforeEach
    void setupPlayer() {
        for (int i = 0; i < 8; i++)
            factories.binaryQuestion();
        player = factories.user();
    }

    private JsonPath startSurvival() {
        return http.reqAs(player)
                .post("/api/game/survival/start")
                .then()
                .statusCode(200)
                .body("sessionId", notNullValue())
                .body("question.id", notNullValue())
                .extract().jsonPath();
    }

    private ValidatableResponse answer(UUID sessionId, UUID questionId, UUID optionId) {
        return http.reqAs(player)
                .body(Map.of(
                        "sessionId", sessionId.toString(),
                        "questionId", questionId.toString(),
                        "optionId", optionId.toString()))
                .post("/api/game/survival/answer")
                .then();
    }

    @Nested
    @DisplayName("Inicio de sesión")
    class Start {

        @Test
        @DisplayName("G1 — start devuelve 200, primera pregunta sin isCorrect en opciones")
        void inicioSesion() {
            JsonPath jp = startSurvival();
            assertThat(jp.getString("sessionId")).isNotNull();
            assertThat(jp.getString("question.id")).isNotNull();
            assertThat((Object) jp.get("question.options[0].isCorrect")).isNull();
        }
    }

    @Nested
    @DisplayName("Respuestas")
    class Answers {

        @Test
        @DisplayName("G2 — Respuesta correcta: correct=true, lifeDelta=0, streak=1, score=50")
        void respuestaCorrecta() {
            JsonPath start = startSurvival();
            UUID sessionId = UUID.fromString(start.getString("sessionId"));
            UUID questionId = UUID.fromString(start.getString("question.id"));
            UUID correctId = testQuery.correctOptionFor(questionId);

            answer(sessionId, questionId, correctId)
                    .statusCode(200)
                    .body("correct", equalTo(true))
                    .body("lifeDelta", equalTo(0))
                    .body("streak", equalTo(1))
                    .body("scoreDelta", equalTo(50));
        }

        @Test
        @DisplayName("G3 — Respuesta incorrecta: correct=false, lifeDelta=-1, streak=0")
        void respuestaIncorrecta() {
            JsonPath start = startSurvival();
            UUID sessionId = UUID.fromString(start.getString("sessionId"));
            UUID questionId = UUID.fromString(start.getString("question.id"));
            UUID wrongId = testQuery.wrongOptionFor(questionId);

            answer(sessionId, questionId, wrongId)
                    .statusCode(200)
                    .body("correct", equalTo(false))
                    .body("lifeDelta", equalTo(-1))
                    .body("livesRemaining", equalTo(2))
                    .body("streak", equalTo(0));
        }

        @Test
        @DisplayName("G4 — Tres aciertos consecutivos: score = 50+100+150 = 300")
        void tresAciertos() {
            JsonPath start = startSurvival();
            UUID sessionId = UUID.fromString(start.getString("sessionId"));
            String currentQuestionId = start.getString("question.id");

            int totalScore = 0;
            for (int i = 0; i < 3; i++) {
                UUID questionId = UUID.fromString(currentQuestionId);
                UUID correctId = testQuery.correctOptionFor(questionId);
                JsonPath resp = answer(sessionId, questionId, correctId)
                        .statusCode(200)
                        .extract().jsonPath();
                totalScore += resp.getInt("scoreDelta");
                if (i < 2) {
                    currentQuestionId = resp.getString("nextQuestion.id");
                }
            }
            assertThat(totalScore).isEqualTo(50 + 100 + 150);
        }

        @Test
        @DisplayName("G5 — Tres fallos → gameOver=true, match FINISHED en BD")
        void tresFallosGameOver() {
            JsonPath start = startSurvival();
            UUID sessionId = UUID.fromString(start.getString("sessionId"));
            String currentQuestionId = start.getString("question.id");

            JsonPath lastResp = null;
            for (int i = 0; i < 3; i++) {
                UUID questionId = UUID.fromString(currentQuestionId);
                UUID wrongId = testQuery.wrongOptionFor(questionId);
                lastResp = answer(sessionId, questionId, wrongId)
                        .statusCode(200)
                        .extract().jsonPath();
                if (i < 2) {
                    currentQuestionId = lastResp.getString("nextQuestion.id");
                }
            }

            assertThat(lastResp.getBoolean("gameOver")).isTrue();
            var match = matchRepo.findById(sessionId).orElseThrow();
            assertThat(match.getStatus()).isEqualTo(MatchStatus.FINISHED);
        }

        @Test
        @DisplayName("G6 — sessionId inexistente → 404 NOT_FOUND")
        void sessionInexistente() {
            answer(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
                    .statusCode(404)
                    .body("error", equalTo("NOT_FOUND"));
        }

        @Test
        @DisplayName("Session de otro usuario → 403 FORBIDDEN")
        void sesionDeOtroUsuario() {
            JsonPath start = startSurvival();
            UUID sessionId = UUID.fromString(start.getString("sessionId"));
            UUID questionId = UUID.fromString(start.getString("question.id"));
            UUID anyOption = testQuery.correctOptionFor(questionId);

            User other = factories.user();
            http.reqAs(other)
                    .body(Map.of(
                            "sessionId", sessionId.toString(),
                            "questionId", questionId.toString(),
                            "optionId", anyOption.toString()))
                    .post("/api/game/survival/answer")
                    .then()
                    .statusCode(403)
                    .body("error", equalTo("FORBIDDEN"));
        }
    }
}
