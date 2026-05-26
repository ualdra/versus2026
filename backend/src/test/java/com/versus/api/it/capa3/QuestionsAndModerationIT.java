package com.versus.api.it.capa3;

import com.versus.api.it.support.AbstractIT;
import com.versus.api.it.support.Factories;
import com.versus.api.moderation.ReportReason;
import com.versus.api.questions.QuestionStatus;
import com.versus.api.questions.QuestionType;
import com.versus.api.questions.domain.Question;
import com.versus.api.questions.repo.QuestionRepository;
import com.versus.api.users.domain.User;

import jakarta.transaction.Transactional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;

@DisplayName("Capa 3 — Preguntas y Moderación")
class QuestionsAndModerationIT extends AbstractIT {

        @Autowired
        QuestionRepository questionRepo;

        // ── Preguntas ─────────────────────────────────────────────────────────────

        @Nested
        @DisplayName("GET /api/questions/random")
        class RandomQuestion {

                @Test
                @DisplayName("Q1 — Devuelve una pregunta ACTIVE")
                void devuelvePreguntaActiva() {
                        factories.binaryQuestion();
                        User u = factories.user();

                        http.reqAs(u)
                                        .get("/api/questions/random")
                                        .then()
                                        .statusCode(200)
                                        .body("id", notNullValue());
                }

                @Test
                @DisplayName("Q2 — Pregunta BINARY no expone isCorrect en las opciones")
                void binaryNoExponeCorrecto() {
                        factories.binaryQuestion();
                        User u = factories.user();

                        http.reqAs(u)
                                        .get("/api/questions/random?type=BINARY")
                                        .then()
                                        .statusCode(200)
                                        .body("options", not(empty()))
                                        .body("options[0].isCorrect", nullValue());
                }

                @Test
                @DisplayName("Q3 — Pregunta NUMERIC no expone correctValue ni tolerancePercent")
                void numericNoExponeCorrecto() {
                        factories.numericQuestion();
                        User u = factories.user();

                        http.reqAs(u)
                                        .get("/api/questions/random?type=NUMERIC")
                                        .then()
                                        .statusCode(200)
                                        .body("unit", notNullValue())
                                        .body("correctValue", nullValue())
                                        .body("tolerancePercent", nullValue());
                }

                @Test
                @DisplayName("Q4 — ?type=BINARY filtra correctamente")
                void filtrarPorTipoBinary() {
                        factories.binaryQuestion();
                        factories.numericQuestion();
                        User u = factories.user();

                        http.reqAs(u)
                                        .get("/api/questions/random?type=BINARY")
                                        .then()
                                        .statusCode(200)
                                        .body("type", equalTo("BINARY"));
                }

                @Test
                @DisplayName("Q5 — Preguntas INACTIVE/PENDING_REVIEW/FLAGGED no se devuelven")
                void preguntasNoActivasExcluidas() {
                        Question inactive = factories.binaryQuestion();
                        inactive.setStatus(QuestionStatus.INACTIVE);
                        questionRepo.save(inactive);

                        User u = factories.user();
                        http.reqAs(u)
                                        .get("/api/questions/random")
                                        .then()
                                        .statusCode(404);
                }
        }

        @Nested
        @DisplayName("GET /api/questions/categories")
        class Categories {

                @Test
                @DisplayName("Q6 — Endpoint público devuelve categorías de preguntas ACTIVE")
                void devuelveCategorias() {
                        factories.binaryQuestion();

                        http.req()
                                        .get("/api/questions/categories")
                                        .then()
                                        .statusCode(200)
                                        .body("$", not(empty()));
                }
        }

        // ── Moderación ────────────────────────────────────────────────────────────

        @Nested
        @DisplayName("POST /api/questions/{id}/report")
        class Report {

                @Test
                @DisplayName("M1 — Player puede reportar una pregunta; crea reporte PENDING")
                void playerReporta() {
                        Question q = factories.binaryQuestion();
                        User u = factories.user();

                        http.reqAs(u)
                                        .body(Map.of("reason", "WRONG_ANSWER"))
                                        .post("/api/questions/" + q.getId() + "/report")
                                        .then()
                                        .statusCode(201)
                                        .body("status", equalTo("PENDING"));
                }

                @Test
                @DisplayName("Reportar 2 veces la misma pregunta → segundo intento 409")
                void dobleReporte() {
                        Question q = factories.binaryQuestion();
                        User u = factories.user();

                        http.reqAs(u)
                                        .body(Map.of("reason", "WRONG_ANSWER"))
                                        .post("/api/questions/" + q.getId() + "/report")
                                        .then().statusCode(201);

                        http.reqAs(u)
                                        .body(Map.of("reason", "WRONG_ANSWER"))
                                        .post("/api/questions/" + q.getId() + "/report")
                                        .then()
                                        .statusCode(409)
                                        .body("error", equalTo("CONFLICT"));
                }

                @Test
                @DisplayName("M2 — 5 reportes distintos sobre la misma pregunta → status FLAGGED")
                void cincoReportesFlagga() {
                        Question q = factories.binaryQuestion();

                        for (int i = 0; i < 5; i++) {
                                User u = factories.user();
                                http.reqAs(u)
                                                .body(Map.of("reason", "WRONG_ANSWER"))
                                                .post("/api/questions/" + q.getId() + "/report")
                                                .then().statusCode(201);
                        }

                        Question updated = questionRepo.findById(q.getId()).orElseThrow();
                        org.assertj.core.api.Assertions.assertThat(updated.getStatus())
                                        .isEqualTo(QuestionStatus.FLAGGED);
                }
        }

        @Nested
        @DisplayName("GET /api/moderation/reports")
        class ListReports {

                @Test
                @DisplayName("M3 — PLAYER recibe 403; MODERATOR recibe 200")
                void accesoSegunRol() {
                        User player = factories.user();
                        User mod = factories.moderator();

                        http.reqAs(player)
                                        .get("/api/moderation/reports")
                                        .then()
                                        .statusCode(403);

                        http.reqAs(mod)
                                        .get("/api/moderation/reports")
                                        .then()
                                        .statusCode(200);
                }
        }

        @Nested
        @DisplayName("PUT /api/moderation/reports/{id}/resolve")
        class Resolve {

                @Test
                @DisplayName("M4 — DISMISS: reporte DISMISSED, pregunta sigue ACTIVE")
                void dismiss() {
                        Question q = factories.binaryQuestion();
                        User player = factories.user();
                        User mod = factories.moderator();

                        String reportId = http.reqAs(player)
                                        .body(Map.of("reason", "WRONG_ANSWER"))
                                        .post("/api/questions/" + q.getId() + "/report")
                                        .then().statusCode(201)
                                        .extract().jsonPath().getString("id");

                        http.reqAs(mod)
                                        .body(Map.of("action", "DISMISS"))
                                        .put("/api/moderation/reports/" + reportId + "/resolve")
                                        .then()
                                        .statusCode(200)
                                        .body("status", equalTo("DISMISSED"));

                        Question updated = questionRepo.findById(q.getId()).orElseThrow();
                        org.assertj.core.api.Assertions.assertThat(updated.getStatus())
                                        .isEqualTo(QuestionStatus.ACTIVE);
                }

                @Test
                @DisplayName("M5 — DELETE_QUESTION: reporte RESOLVED, pregunta INACTIVE")
                void deleteQuestion() {
                        Question q = factories.binaryQuestion();
                        User player = factories.user();
                        User mod = factories.moderator();

                        String reportId = http.reqAs(player)
                                        .body(Map.of("reason", "WRONG_ANSWER"))
                                        .post("/api/questions/" + q.getId() + "/report")
                                        .then().statusCode(201)
                                        .extract().jsonPath().getString("id");

                        http.reqAs(mod)
                                        .body(Map.of("action", "DELETE_QUESTION"))
                                        .put("/api/moderation/reports/" + reportId + "/resolve")
                                        .then()
                                        .statusCode(200)
                                        .body("status", equalTo("RESOLVED"));

                        Question updated = questionRepo.findById(q.getId()).orElseThrow();
                        org.assertj.core.api.Assertions.assertThat(updated.getStatus())
                                        .isEqualTo(QuestionStatus.INACTIVE);
                }

                @Test
                @DisplayName("M6 — PLAYER intenta resolver → 403")
                void playerResuelve() {
                        Question q = factories.binaryQuestion();
                        User player = factories.user();

                        String reportId = http.reqAs(player)
                                        .body(Map.of("reason", "WRONG_ANSWER"))
                                        .post("/api/questions/" + q.getId() + "/report")
                                        .then().statusCode(201)
                                        .extract().jsonPath().getString("id");

                        http.reqAs(player)
                                        .body(Map.of("action", "DISMISS"))
                                        .put("/api/moderation/reports/" + reportId + "/resolve")
                                        .then()
                                        .statusCode(403);
                }
        }
}
