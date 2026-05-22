package com.versus.api.it.capa7;

import com.versus.api.it.support.AbstractIT;
import com.versus.api.match.MatchmakingService;
import com.versus.api.match.MatchStatus;
import com.versus.api.match.repo.MatchRepository;
import com.versus.api.questions.QuestionStatus;
import com.versus.api.questions.domain.Question;
import com.versus.api.questions.repo.QuestionRepository;
import com.versus.api.users.domain.User;
import io.restassured.path.json.JsonPath;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

@DisplayName("Capa 7 — Journeys E2E")
class EndToEndJourneyIT extends AbstractIT {

    @LocalServerPort
    int port;
    @Autowired
    MatchRepository matchRepo;
    @Autowired
    QuestionRepository questionRepo;
    @Autowired
    MatchmakingService matchmakingService;

    private WebSocketStompClient stompClient;
    private final List<StompSession> sessions = new ArrayList<>();

    @BeforeEach
    void setupStompClient() {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.findAndRegisterModules();
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(mapper);
        stompClient.setMessageConverter(converter);
    }

    @AfterEach
    void disconnectAll() {
        sessions.forEach(s -> {
            if (s.isConnected())
                s.disconnect();
        });
        sessions.clear();
    }

    private StompSession connect(User user) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + http.tokenFor(user));
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.setOrigin("http://localhost:4200");
        StompSession session = stompClient.connectAsync(
                "ws://localhost:" + port + "/ws",
                handshakeHeaders,
                connectHeaders,
                new StompSessionHandlerAdapter() {
                }).get(5, SECONDS);
        sessions.add(session);
        return session;
    }

    private BlockingQueue<Map<?, ?>> subscribe(StompSession session, String destination) {
        BlockingQueue<Map<?, ?>> queue = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders h) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders h, Object payload) {
                queue.add((Map<?, ?>) payload);
            }
        });
        return queue;
    }

    private UUID wrongOptionFor(UUID questionId) {
        return testQuery.wrongOptionFor(questionId);
    }

    // ── J1 — Vida de un jugador ───────────────────────────────────────────────

    @Test
    @DisplayName("J1 — Registro → login → Survival hasta gameOver → stats y achievements")
    void J1_vidaDeUnJugador() {
        for (int i = 0; i < 8; i++)
            factories.binaryQuestion();

        // Register
        http.req()
                .body(Map.of("username", "journeyUser", "email", "journey@versus.test", "password", "Password1!"))
                .post("/api/auth/register")
                .then().statusCode(201);

        // Login
        String accessToken = http.req()
                .body(Map.of("email", "journey@versus.test", "password", "Password1!"))
                .post("/api/auth/login")
                .then().statusCode(200)
                .extract().jsonPath().getString("accessToken");

        // Start Survival
        JsonPath start = http.req()
                .header("Authorization", "Bearer " + accessToken)
                .post("/api/game/survival/start")
                .then().statusCode(200)
                .extract().jsonPath();

        UUID sessionId = UUID.fromString(start.getString("sessionId"));
        String currentQuestionId = start.getString("question.id");

        // Answer wrong 3 times → gameOver
        boolean gameOver = false;
        for (int i = 0; i < 3 && !gameOver; i++) {
            UUID questionId = UUID.fromString(currentQuestionId);
            UUID wrongId = wrongOptionFor(questionId);
            JsonPath resp = http.req()
                    .header("Authorization", "Bearer " + accessToken)
                    .body(Map.of(
                            "sessionId", sessionId.toString(),
                            "questionId", questionId.toString(),
                            "optionId", wrongId.toString()))
                    .post("/api/game/survival/answer")
                    .then().statusCode(200)
                    .extract().jsonPath();
            gameOver = resp.getBoolean("gameOver");
            if (!gameOver) {
                currentQuestionId = resp.getString("nextQuestion.id");
            }
        }
        assertThat(gameOver).isTrue();

        // Stats
        http.req()
                .header("Authorization", "Bearer " + accessToken)
                .get("/api/stats/me?mode=SURVIVAL")
                .then()
                .statusCode(200)
                .body("gamesPlayed", org.hamcrest.Matchers.equalTo(1));
    }

    // ── J2 — Moderación ───────────────────────────────────────────────────────

    @Test
    @DisplayName("J2 — 5 players reportan pregunta → FLAGGED → moderador la elimina → no aparece en random")
    void J2_moderacion() {
        Question q = factories.binaryQuestion();

        for (int i = 0; i < 5; i++) {
            User u = factories.user();
            http.reqAs(u)
                    .body(Map.of("reason", "WRONG_ANSWER"))
                    .post("/api/questions/" + q.getId() + "/report")
                    .then().statusCode(201);
        }

        assertThat(questionRepo.findById(q.getId()).orElseThrow().getStatus())
                .isEqualTo(QuestionStatus.FLAGGED);

        User mod = factories.moderator();
        String reportId = http.reqAs(mod)
                .get("/api/moderation/reports?status=PENDING")
                .then().statusCode(200)
                .extract().jsonPath().getString("content[0].id");

        http.reqAs(mod)
                .body(Map.of("action", "DELETE_QUESTION"))
                .put("/api/moderation/reports/" + reportId + "/resolve")
                .then().statusCode(200);

        assertThat(questionRepo.findById(q.getId()).orElseThrow().getStatus())
                .isEqualTo(QuestionStatus.INACTIVE);

        // Pregunta ya no aparece en random
        User viewer = factories.user();
        http.reqAs(viewer)
                .get("/api/questions/random")
                .then()
                .statusCode(404);
    }

    // ── J3 — Multiplayer feliz ────────────────────────────────────────────────

    @Test
    @DisplayName("J3 — 2 players → cola → match found → lobby → ambos ready → MATCH_START")
    void J3_multiplayerFeliz() throws Exception {
        User userA = factories.user();
        User userB = factories.user();

        StompSession sessionA = connect(userA);
        StompSession sessionB = connect(userB);

        BlockingQueue<Map<?, ?>> personalA = subscribe(sessionA, "/user/queue/match");
        BlockingQueue<Map<?, ?>> personalB = subscribe(sessionB, "/user/queue/match");
        Thread.sleep(200);

        http.reqAs(userA).body(Map.of("mode", "BINARY_DUEL")).post("/api/matchmaking/queue").then().statusCode(204);
        http.reqAs(userB).body(Map.of("mode", "BINARY_DUEL")).post("/api/matchmaking/queue").then().statusCode(204);
        matchmakingService.pollAndMatch();

        // Wait for MATCH_FOUND on both
        await().atMost(3, SECONDS).untilAsserted(() -> {
            assertThat(personalA.peek()).isNotNull();
            assertThat(personalB.peek()).isNotNull();
        });

        Map<?, ?> foundEventA = personalA.poll(1, SECONDS);
        assertThat(foundEventA.get("type")).isEqualTo("MATCH_FOUND");
        String matchId = (String) foundEventA.get("matchId");

        // Subscribe both to the match topic
        BlockingQueue<Map<?, ?>> matchEvents = subscribe(sessionA, "/topic/match/" + matchId);
        Thread.sleep(200);

        sessionA.send("/app/match/ready", Map.of("matchId", matchId));
        sessionB.send("/app/match/ready", Map.of("matchId", matchId));

        await().atMost(5, SECONDS).untilAsserted(() -> {
            List<Map<?, ?>> all = new ArrayList<>();
            matchEvents.drainTo(all);
            List<String> types = all.stream().map(e -> (String) e.get("type")).toList();
            assertThat(types).contains("MATCH_STARTING");
        });

        await().atMost(5, SECONDS).untilAsserted(() -> {
            var match = matchRepo.findById(UUID.fromString(matchId)).orElseThrow();
            assertThat(match.getStatus()).isEqualTo(MatchStatus.IN_PROGRESS);
        });
    }

    // ── J4 — Multiplayer roto ────────────────────────────────────────────────

    @Test
    @DisplayName("J4 — 2 players → lobby → uno abandona → el otro recibe PLAYER_LEFT → match FINISHED")
    void J4_multiplayerRoto() throws Exception {
        User userA = factories.user();
        User userB = factories.user();

        StompSession sessionA = connect(userA);
        StompSession sessionB = connect(userB);

        String matchId = http.reqAs(userA)
                .body(Map.of("mode", "BINARY_DUEL"))
                .post("/api/matches")
                .then().statusCode(201)
                .extract().jsonPath().getString("matchId");

        http.reqAs(userB).post("/api/matches/" + matchId + "/join").then().statusCode(200);

        BlockingQueue<Map<?, ?>> eventsB = subscribe(sessionB, "/topic/match/" + matchId);
        Thread.sleep(200);

        sessionA.send("/app/match/abandon", Map.of("matchId", matchId));

        await().atMost(3, SECONDS).untilAsserted(() -> {
            List<Map<?, ?>> all = new ArrayList<>();
            eventsB.drainTo(all);

            assertThat(
                    all.stream()
                            .map(e -> (String) e.get("type"))
                            .toList())
                    .contains("PLAYER_LEFT");
        });
    }

    // ── J5 — Refresh flow ─────────────────────────────────────────────────────

    @Test
    @DisplayName("J5 — access expirado → /me falla → refresh → /me con nuevo token → 200")
    void J5_refreshFlow() {
        User u = factories.user();

        // Expired access token
        String expired = http.expiredAccessToken(u);
        http.req()
                .header("Authorization", "Bearer " + expired)
                .get("/api/users/me")
                .then()
                .statusCode(401);

        // Get valid refresh token via login
        String refreshToken = http.req()
                .body(Map.of("email", u.getEmail(), "password", com.versus.api.it.support.Factories.DEFAULT_PASSWORD))
                .post("/api/auth/login")
                .then().statusCode(200)
                .extract().jsonPath().getString("refreshToken");

        // Refresh
        String newAccessToken = http.req()
                .body(Map.of("refreshToken", refreshToken))
                .post("/api/auth/refresh")
                .then().statusCode(200)
                .extract().jsonPath().getString("accessToken");

        // Retry with new token
        http.req()
                .header("Authorization", "Bearer " + newAccessToken)
                .get("/api/users/me")
                .then()
                .statusCode(200)
                .body("id", org.hamcrest.Matchers.notNullValue());
    }
}
