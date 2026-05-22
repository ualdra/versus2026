package com.versus.api.it.capa5;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.versus.api.it.support.AbstractIT;
import com.versus.api.match.MatchStatus;
import com.versus.api.match.MatchmakingService;
import com.versus.api.match.repo.MatchRepository;
import com.versus.api.users.domain.User;
import com.versus.api.websocket.MatchEventEnvelope;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

@DisplayName("Capa 5 — WebSocket Lobby")
class WebSocketLobbyIT extends AbstractIT {

    @LocalServerPort
    int port;
    @Autowired
    MatchRepository matchRepo;
    @Autowired
    MatchmakingService matchmakingService;
    @Autowired
    ObjectMapper objectMapper;

    private WebSocketStompClient stompClient;
    private final List<StompSession> sessions = new ArrayList<>();

    @BeforeEach
    void setupStompClient() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
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
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add((Map<?, ?>) payload);
            }
        });
        return queue;
    }

    private String createMatchViaHttp(User u) {
        return http.reqAs(u)
                .body(Map.of("mode", "BINARY_DUEL"))
                .post("/api/matches")
                .then().statusCode(201)
                .extract().jsonPath().getString("matchId");
    }

    @Test
    @DisplayName("W1 — STOMP CONNECT con token válido → conexión establecida")
    void connectConTokenValido() throws Exception {
        User u = factories.user();
        StompSession session = connect(u);
        assertThat(session.isConnected()).isTrue();
    }

    @Test
    @DisplayName("W2 — STOMP CONNECT sin Authorization header → falla")
    void connectSinToken() {
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.setOrigin("http://localhost:4200");

        CompletableFuture<StompSession> future = stompClient.connectAsync(
                "ws://localhost:" + port + "/ws",
                handshakeHeaders,
                new StompHeaders(),
                new StompSessionHandlerAdapter() {
                });

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> future.get(3, SECONDS))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("W3 — STOMP CONNECT con token expirado → falla")
    void connectConTokenExpirado() {
        User u = factories.user();
        String expired = http.expiredAccessToken(u);

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + expired);

        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.setOrigin("http://localhost:4200");

        CompletableFuture<StompSession> future = stompClient.connectAsync(
                "ws://localhost:" + port + "/ws",
                handshakeHeaders,
                connectHeaders,
                new StompSessionHandlerAdapter() {
                });

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> future.get(3, SECONDS))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("W4 — STOMP CONNECT con token malformado → falla")
    void connectConTokenMalformado() {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer no-es-un-jwt");

        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.setOrigin("http://localhost:4200");

        CompletableFuture<StompSession> future = stompClient.connectAsync(
                "ws://localhost:" + port + "/ws",
                handshakeHeaders,
                connectHeaders,
                new StompSessionHandlerAdapter() {
                });

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> future.get(3, SECONDS))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("W5 — Player B se une al match de A; A recibe PLAYER_JOINED con datos de B")
    void playerJoinedEvent() throws Exception {
        User userA = factories.user();
        User userB = factories.user();

        StompSession sessionA = connect(userA);
        String matchId = createMatchViaHttp(userA);

        BlockingQueue<Map<?, ?>> eventsA = subscribe(sessionA, "/topic/match/" + matchId);

        // B joins via HTTP
        http.reqAs(userB)
                .post("/api/matches/" + matchId + "/join")
                .then().statusCode(200);

        Map<?, ?> event = eventsA.poll(3, SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.get("type")).isEqualTo("PLAYER_JOINED");
    }

    @Test
    @DisplayName("W6 — A envía /app/match/ready; ambos reciben PLAYER_READY")
    void playerReadyEvent() throws Exception {
        User userA = factories.user();
        User userB = factories.user();

        StompSession sessionA = connect(userA);
        StompSession sessionB = connect(userB);

        String matchId = createMatchViaHttp(userA);
        http.reqAs(userB).post("/api/matches/" + matchId + "/join").then().statusCode(200);

        // Subscribe both to the topic BEFORE sending ready
        BlockingQueue<Map<?, ?>> eventsA = subscribe(sessionA, "/topic/match/" + matchId);
        BlockingQueue<Map<?, ?>> eventsB = subscribe(sessionB, "/topic/match/" + matchId);

        // Give subscriptions time to register
        Thread.sleep(200);

        sessionA.send("/app/match/ready", Map.of("matchId", matchId));

        await().atMost(3, SECONDS).untilAsserted(() -> {
            Map<?, ?> ev = eventsA.peek();
            assertThat(ev).isNotNull();
            assertThat(ev.get("type")).isEqualTo("PLAYER_READY");
        });

        await().atMost(3, SECONDS).untilAsserted(() -> {
            Map<?, ?> ev = eventsB.peek();
            assertThat(ev).isNotNull();
        });
    }

    @Test
    @DisplayName("W7 — Ambos ready → MATCH_STARTING + MATCH_START emitidos; match IN_PROGRESS en BD")
    void matchStartSequence() throws Exception {
        User userA = factories.user();
        User userB = factories.user();

        StompSession sessionA = connect(userA);
        StompSession sessionB = connect(userB);

        String matchId = createMatchViaHttp(userA);
        http.reqAs(userB).post("/api/matches/" + matchId + "/join").then().statusCode(200);

        BlockingQueue<Map<?, ?>> eventsA = subscribe(sessionA, "/topic/match/" + matchId);
        Thread.sleep(200);

        sessionA.send("/app/match/ready", Map.of("matchId", matchId));
        sessionB.send("/app/match/ready", Map.of("matchId", matchId));

        List<String> typesA = new ArrayList<>();
        await().atMost(5, SECONDS).untilAsserted(() -> {
            List<Map<?, ?>> chunk = new ArrayList<>();
            eventsA.drainTo(chunk);
            chunk.stream().map(e -> (String) e.get("type")).forEach(typesA::add);
            assertThat(typesA).contains("MATCH_STARTING", "MATCH_START");
        });

        await().atMost(5, SECONDS).untilAsserted(() -> {
            var match = matchRepo.findById(UUID.fromString(matchId)).orElseThrow();
            assertThat(match.getStatus()).isEqualTo(MatchStatus.IN_PROGRESS);
        });
    }

    @Test
    @DisplayName("W8 — A abandona antes de MATCH_START; B recibe PLAYER_LEFT")
    void playerLeftEvent() throws Exception {
        User userA = factories.user();
        User userB = factories.user();

        StompSession sessionA = connect(userA);
        StompSession sessionB = connect(userB);

        String matchId = createMatchViaHttp(userA);
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

    @Test
    @DisplayName("W9 — Matchmaking: 2 players en cola → pollAndMatch → reciben MATCH_FOUND")
    void matchFoundViaMatchmaking() throws Exception {
        User userA = factories.user();
        User userB = factories.user();

        StompSession sessionA = connect(userA);
        StompSession sessionB = connect(userB);

        BlockingQueue<Map<?, ?>> personalA = subscribe(sessionA, "/user/queue/match");
        BlockingQueue<Map<?, ?>> personalB = subscribe(sessionB, "/user/queue/match");
        Thread.sleep(200);

        http.reqAs(userA).body(Map.of("mode", "BINARY_DUEL")).post("/api/matchmaking/queue").then().statusCode(204);
        http.reqAs(userB).body(Map.of("mode", "BINARY_DUEL")).post("/api/matchmaking/queue").then().statusCode(204);

        // Trigger matchmaking directly — deterministic, no sleep
        matchmakingService.pollAndMatch();

        await().atMost(3, SECONDS).untilAsserted(() -> {
            Map<?, ?> evA = personalA.peek();
            Map<?, ?> evB = personalB.peek();
            assertThat(evA).isNotNull();
            assertThat(evB).isNotNull();
            assertThat(evA.get("type")).isEqualTo("MATCH_FOUND");
            assertThat(evB.get("type")).isEqualTo("MATCH_FOUND");
        });
    }
}
