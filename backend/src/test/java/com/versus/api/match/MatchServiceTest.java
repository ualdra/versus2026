package com.versus.api.match;

import com.versus.api.common.exception.ApiException;
import com.versus.api.common.exception.ErrorCode;
import com.versus.api.match.domain.Match;
import com.versus.api.match.domain.MatchAnswer;
import com.versus.api.match.domain.MatchPlayer;
import com.versus.api.match.domain.MatchPlayerId;
import com.versus.api.match.domain.MatchRound;
import com.versus.api.match.dto.MatchDetailResponse;
import com.versus.api.match.dto.MatchFoundEvent;
import com.versus.api.match.dto.MatchHistoryItemResponse;
import com.versus.api.match.dto.PlayerInLobbyDto;
import com.versus.api.match.repo.MatchAnswerRepository;
import com.versus.api.match.repo.MatchPlayerRepository;
import com.versus.api.match.repo.MatchRepository;
import com.versus.api.match.repo.MatchRoundRepository;
import com.versus.api.match.state.LiveMatchState;
import com.versus.api.questions.QuestionStatus;
import com.versus.api.questions.QuestionType;
import com.versus.api.questions.domain.Question;
import com.versus.api.questions.repo.QuestionRepository;
import com.versus.api.users.Role;
import com.versus.api.users.domain.User;
import com.versus.api.users.repo.UserRepository;
import com.versus.api.websocket.MatchEventEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("MatchService")
@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    @Mock MatchRepository matchRepository;
    @Mock MatchPlayerRepository matchPlayers;
    @Mock MatchRoundRepository matchRounds;
    @Mock MatchAnswerRepository matchAnswers;
    @Mock QuestionRepository questions;
    @Mock UserRepository userRepository;
    @Mock SimpMessagingTemplate broker;

    @InjectMocks MatchService matchService;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(matchService, "countdownSeconds", 1);
    }

    static final UUID USER_ID  = UUID.fromString("aaaa0000-0000-0000-0000-000000000001");
    static final UUID MATCH_ID = UUID.fromString("bbbb0000-0000-0000-0000-000000000002");

    private Match match(UUID id, GameMode mode) {
        return Match.builder()
                .id(id).mode(mode)
                .createdAt(Instant.now()).finishedAt(Instant.now())
                .build();
    }

    private MatchPlayer player(UUID matchId, UUID userId, int score, MatchResult result) {
        return MatchPlayer.builder()
                .id(new MatchPlayerId(matchId, userId))
                .score(score).livesRemaining(2)
                .currentStreak(1).bestStreakInMatch(3)
                .roundsPlayed(5).result(result)
                .build();
    }

    private User user(UUID id, String username) {
        return User.builder()
                .id(id).username(username).email(username + "@test.com")
                .passwordHash("x").role(Role.PLAYER)
                .isActive(true).createdAt(Instant.now())
                .build();
    }

    private User user(String name) {
        return User.builder()
                .id(UUID.randomUUID()).username(name).email(name + "@versus.com")
                .passwordHash("$2a$hash").role(Role.PLAYER).isActive(true)
                .build();
    }

    private void stubMatchSave() {
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> {
            Match m = inv.getArgument(0);
            if (m.getId() == null) m.setId(UUID.randomUUID());
            if (m.getCreatedAt() == null) m.setCreatedAt(Instant.now());
            return m;
        });
        when(matchRepository.findByRoomCode(any())).thenReturn(Optional.empty());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // getHistory
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("getHistory")
    @Nested
    class GetHistory {

        @DisplayName("Sin filtro de modo usa findFinishedByUserId")
        @Test
        void sinFiltro_usaFindFinishedByUserId() {
            when(matchRepository.findFinishedByUserId(eq(USER_ID), any())).thenReturn(Page.empty());

            matchService.getHistory(USER_ID, 0, 20, null);

            verify(matchRepository).findFinishedByUserId(eq(USER_ID), any());
            verify(matchRepository, never()).findFinishedByUserIdAndMode(any(), any(), any());
        }

        @DisplayName("Con filtro de modo usa findFinishedByUserIdAndMode")
        @Test
        void conFiltroModo_usaFindFinishedByUserIdAndMode() {
            when(matchRepository.findFinishedByUserIdAndMode(eq(USER_ID), eq("PRECISION"), any()))
                    .thenReturn(Page.empty());

            matchService.getHistory(USER_ID, 0, 20, GameMode.PRECISION);

            verify(matchRepository).findFinishedByUserIdAndMode(eq(USER_ID), eq("PRECISION"), any());
            verify(matchRepository, never()).findFinishedByUserId(any(), any());
        }

        @DisplayName("Resultado vacío devuelve página vacía sin llamar a MatchPlayerRepository")
        @Test
        void resultadoVacio_devuelvePageVacia() {
            when(matchRepository.findFinishedByUserId(any(), any())).thenReturn(Page.empty());

            Page<MatchHistoryItemResponse> result = matchService.getHistory(USER_ID, 0, 20, null);

            assertThat(result.getTotalElements()).isZero();
            verify(matchPlayers, never()).findByIdMatchIdAndIdUserId(any(), any());
        }

        @DisplayName("Size superior a 50 se clampea a 50")
        @Test
        void sizeMaximoSuperado_seClampea_a_50() {
            when(matchRepository.findFinishedByUserId(any(), any())).thenReturn(Page.empty());
            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

            matchService.getHistory(USER_ID, 0, 200, null);

            verify(matchRepository).findFinishedByUserId(any(), captor.capture());
            assertThat(captor.getValue().getPageSize()).isEqualTo(50);
        }

        @DisplayName("Size igual a 50 no se modifica")
        @Test
        void sizeIgual_50_noSeCambia() {
            when(matchRepository.findFinishedByUserId(any(), any())).thenReturn(Page.empty());
            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

            matchService.getHistory(USER_ID, 0, 50, null);

            verify(matchRepository).findFinishedByUserId(any(), captor.capture());
            assertThat(captor.getValue().getPageSize()).isEqualTo(50);
        }

        @DisplayName("Size inferior a 50 se respeta")
        @Test
        void sizeInferior_50_seRespeta() {
            when(matchRepository.findFinishedByUserId(any(), any())).thenReturn(Page.empty());
            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

            matchService.getHistory(USER_ID, 0, 10, null);

            verify(matchRepository).findFinishedByUserId(any(), captor.capture());
            assertThat(captor.getValue().getPageSize()).isEqualTo(10);
        }

        @DisplayName("Partida solitaria: opponent es null")
        @Test
        void partida_solitaria_opponentEsNull() {
            Match m = match(MATCH_ID, GameMode.SURVIVAL);
            MatchPlayer mp = player(MATCH_ID, USER_ID, 300, MatchResult.WIN);

            when(matchRepository.findFinishedByUserId(any(), any()))
                    .thenReturn(new PageImpl<>(List.of(m)));
            when(matchPlayers.findByIdMatchIdAndIdUserId(MATCH_ID, USER_ID))
                    .thenReturn(Optional.of(mp));

            Page<MatchHistoryItemResponse> result = matchService.getHistory(USER_ID, 0, 20, null);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).opponent()).isNull();
        }

        @DisplayName("Partida multijugador incluye opponent con username")
        @Test
        void partida_multijugador_incluyeOpponent() {
            UUID opponentId = UUID.randomUUID();
            Match m = match(MATCH_ID, GameMode.BINARY_DUEL);
            MatchPlayer myPlayer    = player(MATCH_ID, USER_ID, 300, MatchResult.WIN);
            MatchPlayer theirPlayer = player(MATCH_ID, opponentId, 200, MatchResult.LOSS);

            when(matchRepository.findFinishedByUserId(any(), any()))
                    .thenReturn(new PageImpl<>(List.of(m)));
            when(matchPlayers.findByIdMatchIdAndIdUserId(MATCH_ID, USER_ID))
                    .thenReturn(Optional.of(myPlayer));
            when(matchPlayers.findByIdMatchId(MATCH_ID))
                    .thenReturn(List.of(myPlayer, theirPlayer));
            when(userRepository.findById(opponentId))
                    .thenReturn(Optional.of(user(opponentId, "Rival")));

            Page<MatchHistoryItemResponse> result = matchService.getHistory(USER_ID, 0, 20, null);

            assertThat(result.getContent().get(0).opponent()).isNotNull();
            assertThat(result.getContent().get(0).opponent().username()).isEqualTo("Rival");
        }

        @DisplayName("Mapea campos básicos correctamente")
        @Test
        void mapeaCamposCorrectos() {
            Match m = match(MATCH_ID, GameMode.SURVIVAL);
            MatchPlayer mp = player(MATCH_ID, USER_ID, 450, MatchResult.WIN);

            when(matchRepository.findFinishedByUserId(any(), any()))
                    .thenReturn(new PageImpl<>(List.of(m)));
            when(matchPlayers.findByIdMatchIdAndIdUserId(MATCH_ID, USER_ID))
                    .thenReturn(Optional.of(mp));

            MatchHistoryItemResponse item = matchService.getHistory(USER_ID, 0, 20, null)
                    .getContent().get(0);

            assertThat(item.id()).isEqualTo(MATCH_ID);
            assertThat(item.mode()).isEqualTo(GameMode.SURVIVAL);
            assertThat(item.result()).isEqualTo(MatchResult.WIN);
            assertThat(item.score()).isEqualTo(450);
            assertThat(item.finishedAt()).isNotNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // getDetail
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("getDetail")
    @Nested
    class GetDetail {

        @DisplayName("Partida no encontrada lanza NOT_FOUND")
        @Test
        void partidaNoEncontrada_lanzaNotFound() {
            when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> matchService.getDetail(MATCH_ID, USER_ID))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode())
                            .isEqualTo(ErrorCode.NOT_FOUND));
        }

        @DisplayName("Usuario no participante lanza FORBIDDEN")
        @Test
        void usuarioNoParticipante_lanzaForbidden() {
            when(matchRepository.findById(MATCH_ID))
                    .thenReturn(Optional.of(match(MATCH_ID, GameMode.SURVIVAL)));
            when(matchPlayers.findByIdMatchIdAndIdUserId(MATCH_ID, USER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> matchService.getDetail(MATCH_ID, USER_ID))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode())
                            .isEqualTo(ErrorCode.FORBIDDEN));
        }

        @DisplayName("Éxito: devuelve detalle completo con jugadores y rondas")
        @Test
        void exitoso_devuelveDetalleCompleto() {
            UUID opponentId = UUID.randomUUID();
            UUID roundId1   = UUID.randomUUID();
            UUID roundId2   = UUID.randomUUID();
            UUID qId1       = UUID.randomUUID();
            UUID qId2       = UUID.randomUUID();

            MatchPlayer myPlayer    = player(MATCH_ID, USER_ID, 300, MatchResult.WIN);
            MatchPlayer theirPlayer = player(MATCH_ID, opponentId, 200, MatchResult.LOSS);

            MatchRound round1 = MatchRound.builder().id(roundId1).matchId(MATCH_ID)
                    .questionId(qId1).roundNumber(1).createdAt(Instant.now()).build();
            MatchRound round2 = MatchRound.builder().id(roundId2).matchId(MATCH_ID)
                    .questionId(qId2).roundNumber(2).createdAt(Instant.now()).build();

            MatchAnswer answer1 = MatchAnswer.builder().roundId(roundId1).userId(USER_ID)
                    .answerGiven("42").isCorrect(true).deviation(1.5).lifeDelta(1)
                    .answeredAt(Instant.now()).build();
            MatchAnswer answer2 = MatchAnswer.builder().roundId(roundId2).userId(USER_ID)
                    .answerGiven("100").isCorrect(false).deviation(5.0).lifeDelta(-1)
                    .answeredAt(Instant.now()).build();

            Question q1 = Question.builder().id(qId1).text("¿Pregunta 1?")
                    .type(QuestionType.NUMERIC).status(QuestionStatus.ACTIVE).build();
            Question q2 = Question.builder().id(qId2).text("¿Pregunta 2?")
                    .type(QuestionType.NUMERIC).status(QuestionStatus.ACTIVE).build();

            when(matchRepository.findById(MATCH_ID))
                    .thenReturn(Optional.of(match(MATCH_ID, GameMode.PRECISION)));
            when(matchPlayers.findByIdMatchIdAndIdUserId(MATCH_ID, USER_ID))
                    .thenReturn(Optional.of(myPlayer));
            when(matchPlayers.findByIdMatchId(MATCH_ID))
                    .thenReturn(List.of(myPlayer, theirPlayer));
            when(userRepository.findAllById(any()))
                    .thenReturn(List.of(user(USER_ID, "Player1"), user(opponentId, "Player2")));
            when(matchRounds.findByMatchIdOrderByRoundNumber(MATCH_ID))
                    .thenReturn(List.of(round1, round2));
            when(questions.findAllById(any())).thenReturn(List.of(q1, q2));
            when(matchAnswers.findByRoundIdIn(any())).thenReturn(List.of(answer1, answer2));

            MatchDetailResponse response = matchService.getDetail(MATCH_ID, USER_ID);

            assertThat(response.id()).isEqualTo(MATCH_ID);
            assertThat(response.players()).hasSize(2);
            assertThat(response.rounds()).hasSize(2);
            assertThat(response.rounds().get(0).correct()).isTrue();
            assertThat(response.rounds().get(1).correct()).isFalse();
        }

        @DisplayName("Rondas sin respuestas: correct=false y answerGiven vacío")
        @Test
        void rondasSinRespuestas_correctEsFalse_answerGivenVacio() {
            UUID roundId    = UUID.randomUUID();
            UUID questionId = UUID.randomUUID();

            MatchPlayer mp = player(MATCH_ID, USER_ID, 0, MatchResult.LOSS);
            MatchRound round = MatchRound.builder().id(roundId).matchId(MATCH_ID)
                    .questionId(questionId).roundNumber(1).createdAt(Instant.now()).build();
            Question q = Question.builder().id(questionId).text("¿Texto?")
                    .type(QuestionType.BINARY).status(QuestionStatus.ACTIVE).build();

            when(matchRepository.findById(MATCH_ID))
                    .thenReturn(Optional.of(match(MATCH_ID, GameMode.SURVIVAL)));
            when(matchPlayers.findByIdMatchIdAndIdUserId(MATCH_ID, USER_ID))
                    .thenReturn(Optional.of(mp));
            when(matchPlayers.findByIdMatchId(MATCH_ID)).thenReturn(List.of(mp));
            when(userRepository.findAllById(any())).thenReturn(List.of());
            when(matchRounds.findByMatchIdOrderByRoundNumber(MATCH_ID)).thenReturn(List.of(round));
            when(questions.findAllById(any())).thenReturn(List.of(q));
            when(matchAnswers.findByRoundIdIn(any())).thenReturn(List.of());

            MatchDetailResponse response = matchService.getDetail(MATCH_ID, USER_ID);

            assertThat(response.rounds()).hasSize(1);
            assertThat(response.rounds().get(0).correct()).isFalse();
            assertThat(response.rounds().get(0).answerGiven()).isEmpty();
            assertThat(response.rounds().get(0).deviation()).isNull();
        }

        @DisplayName("Respuesta con desviación mapea deviation correctamente")
        @Test
        void respuestaConDesviacion_mapeaDeviationCorrectamente() {
            UUID roundId    = UUID.randomUUID();
            UUID questionId = UUID.randomUUID();

            MatchPlayer mp = player(MATCH_ID, USER_ID, 100, MatchResult.WIN);
            MatchRound round = MatchRound.builder().id(roundId).matchId(MATCH_ID)
                    .questionId(questionId).roundNumber(1).createdAt(Instant.now()).build();
            MatchAnswer answer = MatchAnswer.builder().roundId(roundId).userId(USER_ID)
                    .answerGiven("500").isCorrect(false).deviation(7.5).lifeDelta(-1)
                    .answeredAt(Instant.now()).build();
            Question q = Question.builder().id(questionId).text("¿Cuántos?")
                    .type(QuestionType.NUMERIC).status(QuestionStatus.ACTIVE).build();

            when(matchRepository.findById(MATCH_ID))
                    .thenReturn(Optional.of(match(MATCH_ID, GameMode.PRECISION)));
            when(matchPlayers.findByIdMatchIdAndIdUserId(MATCH_ID, USER_ID))
                    .thenReturn(Optional.of(mp));
            when(matchPlayers.findByIdMatchId(MATCH_ID)).thenReturn(List.of(mp));
            when(userRepository.findAllById(any())).thenReturn(List.of());
            when(matchRounds.findByMatchIdOrderByRoundNumber(MATCH_ID)).thenReturn(List.of(round));
            when(questions.findAllById(any())).thenReturn(List.of(q));
            when(matchAnswers.findByRoundIdIn(any())).thenReturn(List.of(answer));

            MatchDetailResponse response = matchService.getDetail(MATCH_ID, USER_ID);

            assertThat(response.rounds().get(0).deviation()).isEqualTo(7.5);
        }

        @DisplayName("Pregunta ausente en el mapa: questionText vacío")
        @Test
        void preguntaAusente_questionTextVacio() {
            UUID roundId    = UUID.randomUUID();
            UUID questionId = UUID.randomUUID();

            MatchPlayer mp = player(MATCH_ID, USER_ID, 0, MatchResult.LOSS);
            MatchRound round = MatchRound.builder().id(roundId).matchId(MATCH_ID)
                    .questionId(questionId).roundNumber(1).createdAt(Instant.now()).build();

            when(matchRepository.findById(MATCH_ID))
                    .thenReturn(Optional.of(match(MATCH_ID, GameMode.SURVIVAL)));
            when(matchPlayers.findByIdMatchIdAndIdUserId(MATCH_ID, USER_ID))
                    .thenReturn(Optional.of(mp));
            when(matchPlayers.findByIdMatchId(MATCH_ID)).thenReturn(List.of(mp));
            when(userRepository.findAllById(any())).thenReturn(List.of());
            when(matchRounds.findByMatchIdOrderByRoundNumber(MATCH_ID)).thenReturn(List.of(round));
            when(questions.findAllById(any())).thenReturn(List.of());
            when(matchAnswers.findByRoundIdIn(any())).thenReturn(List.of());

            MatchDetailResponse response = matchService.getDetail(MATCH_ID, USER_ID);

            assertThat(response.rounds().get(0).questionText()).isEmpty();
        }

        @DisplayName("Usuario ausente en el mapa: username es 'Unknown'")
        @Test
        void usuarioAusenteEnMap_usernameEsUnknown() {
            UUID roundId = UUID.randomUUID();

            MatchPlayer mp = player(MATCH_ID, USER_ID, 0, MatchResult.LOSS);
            MatchRound round = MatchRound.builder().id(roundId).matchId(MATCH_ID)
                    .questionId(UUID.randomUUID()).roundNumber(1).createdAt(Instant.now()).build();

            when(matchRepository.findById(MATCH_ID))
                    .thenReturn(Optional.of(match(MATCH_ID, GameMode.SURVIVAL)));
            when(matchPlayers.findByIdMatchIdAndIdUserId(MATCH_ID, USER_ID))
                    .thenReturn(Optional.of(mp));
            when(matchPlayers.findByIdMatchId(MATCH_ID)).thenReturn(List.of(mp));
            when(userRepository.findAllById(any())).thenReturn(List.of());
            when(matchRounds.findByMatchIdOrderByRoundNumber(MATCH_ID)).thenReturn(List.of(round));
            when(questions.findAllById(any())).thenReturn(List.of());
            when(matchAnswers.findByRoundIdIn(any())).thenReturn(List.of());

            MatchDetailResponse response = matchService.getDetail(MATCH_ID, USER_ID);

            assertThat(response.players()).hasSize(1);
            assertThat(response.players().get(0).username()).isEqualTo("Unknown");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // createMatch
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("createMatch")
    @Nested
    class Create {

        @DisplayName("Camino feliz: persiste Match en BD y registra LiveMatchState")
        @Test
        void caminoFeliz() {
            stubMatchSave();
            UUID owner = UUID.randomUUID();

            LiveMatchState state = matchService.createMatch(GameMode.BINARY_DUEL, owner);

            assertThat(state.getMatchId()).isNotNull();
            assertThat(state.getMode()).isEqualTo(GameMode.BINARY_DUEL);
            assertThat(state.getStatus()).isEqualTo(MatchStatus.WAITING);
            assertThat(state.getRoomCode()).hasSize(6);
            assertThat(matchService.liveMatchesView()).containsKey(state.getMatchId());
        }

        @DisplayName("Modo single-player se rechaza con VALIDATION_ERROR")
        @Test
        void modoSingleplayer_rechaza() {
            assertThatThrownBy(() -> matchService.createMatch(GameMode.SURVIVAL, UUID.randomUUID()))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode())
                            .isEqualTo(ErrorCode.VALIDATION_ERROR));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // addPlayer
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("addPlayer")
    @Nested
    class AddPlayer {

        @DisplayName("Camino feliz: añade jugador y emite PLAYER_JOINED al topic")
        @Test
        void caminoFeliz() {
            stubMatchSave();
            User u = user("alice");
            when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));

            LiveMatchState state = matchService.createMatch(GameMode.BINARY_DUEL, u.getId());
            matchService.addPlayer(state.getMatchId(), u.getId());

            assertThat(state.getPlayers()).containsKey(u.getId());

            ArgumentCaptor<MatchEventEnvelope> ev = ArgumentCaptor.forClass(MatchEventEnvelope.class);
            verify(broker).convertAndSend(eq("/topic/match/" + state.getMatchId()), ev.capture());
            assertThat(ev.getValue().type()).isEqualTo("PLAYER_JOINED");
        }

        @DisplayName("Añadir el mismo usuario dos veces es idempotente y no re-emite evento")
        @Test
        void idempotente() {
            stubMatchSave();
            User u = user("alice");
            when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));

            LiveMatchState state = matchService.createMatch(GameMode.BINARY_DUEL, u.getId());
            matchService.addPlayer(state.getMatchId(), u.getId());
            matchService.addPlayer(state.getMatchId(), u.getId());

            verify(broker, times(1))
                    .convertAndSend(eq("/topic/match/" + state.getMatchId()), any(Object.class));
        }

        @DisplayName("Añadir cuando la sala está llena lanza CONFLICT")
        @Test
        void salaLlena_lanzaConflict() {
            stubMatchSave();
            User u1 = user("alice");
            User u2 = user("bob");
            when(userRepository.findById(u1.getId())).thenReturn(Optional.of(u1));
            when(userRepository.findById(u2.getId())).thenReturn(Optional.of(u2));

            LiveMatchState state = matchService.createMatch(GameMode.BINARY_DUEL, u1.getId());
            matchService.addPlayer(state.getMatchId(), u1.getId());
            matchService.addPlayer(state.getMatchId(), u2.getId());

            assertThatThrownBy(() -> matchService.addPlayer(state.getMatchId(), UUID.randomUUID()))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode())
                            .isEqualTo(ErrorCode.CONFLICT));
        }

        @DisplayName("Match inexistente lanza NOT_FOUND")
        @Test
        void matchInexistente_lanzaNotFound() {
            assertThatThrownBy(() -> matchService.addPlayer(UUID.randomUUID(), UUID.randomUUID()))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode())
                            .isEqualTo(ErrorCode.NOT_FOUND));
        }

        @DisplayName("Usuario inexistente lanza NOT_FOUND")
        @Test
        void usuarioInexistente_lanzaNotFound() {
            stubMatchSave();
            UUID owner = UUID.randomUUID();
            UUID phantom = UUID.randomUUID();
            when(userRepository.findById(phantom)).thenReturn(Optional.empty());

            LiveMatchState state = matchService.createMatch(GameMode.BINARY_DUEL, owner);

            assertThatThrownBy(() -> matchService.addPlayer(state.getMatchId(), phantom))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode())
                            .isEqualTo(ErrorCode.NOT_FOUND));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // markReady
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("markReady")
    @Nested
    class MarkReady {

        @DisplayName("Cambia el estado del jugador y emite PLAYER_READY")
        @Test
        void cambiaEstado_yEmite() {
            stubMatchSave();
            User u = user("alice");
            when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));

            LiveMatchState state = matchService.createMatch(GameMode.BINARY_DUEL, u.getId());
            matchService.addPlayer(state.getMatchId(), u.getId());
            clearInvocations(broker);

            matchService.markReady(state.getMatchId(), u.getId(), true);

            assertThat(state.getPlayers().get(u.getId()).isReady()).isTrue();
            ArgumentCaptor<MatchEventEnvelope> ev = ArgumentCaptor.forClass(MatchEventEnvelope.class);
            verify(broker).convertAndSend(eq("/topic/match/" + state.getMatchId()), ev.capture());
            assertThat(ev.getValue().type()).isEqualTo("PLAYER_READY");
        }

        @DisplayName("Marcar ready cuando ya está ready no re-emite (idempotente)")
        @Test
        void idempotente() {
            stubMatchSave();
            User u = user("alice");
            when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));

            LiveMatchState state = matchService.createMatch(GameMode.BINARY_DUEL, u.getId());
            matchService.addPlayer(state.getMatchId(), u.getId());
            matchService.markReady(state.getMatchId(), u.getId(), true);
            clearInvocations(broker);

            matchService.markReady(state.getMatchId(), u.getId(), true);

            verifyNoInteractions(broker);
        }

        @DisplayName("Cuando todos los jugadores están listos emite MATCH_STARTING")
        @Test
        void todosListos_emiteMatchStarting() {
            stubMatchSave();
            User u1 = user("alice");
            User u2 = user("bob");
            when(userRepository.findById(u1.getId())).thenReturn(Optional.of(u1));
            when(userRepository.findById(u2.getId())).thenReturn(Optional.of(u2));

            LiveMatchState state = matchService.createMatch(GameMode.BINARY_DUEL, u1.getId());
            matchService.addPlayer(state.getMatchId(), u1.getId());
            matchService.addPlayer(state.getMatchId(), u2.getId());
            matchService.markReady(state.getMatchId(), u1.getId(), true);
            clearInvocations(broker);

            matchService.markReady(state.getMatchId(), u2.getId(), true);

            ArgumentCaptor<MatchEventEnvelope> ev = ArgumentCaptor.forClass(MatchEventEnvelope.class);
            verify(broker, atLeast(2))
                    .convertAndSend(eq("/topic/match/" + state.getMatchId()), ev.capture());
            assertThat(ev.getAllValues())
                    .extracting(MatchEventEnvelope::type)
                    .contains("PLAYER_READY", "MATCH_STARTING");
        }

        @DisplayName("Jugador que no está en la partida lanza FORBIDDEN")
        @Test
        void noEsJugador_lanzaForbidden() {
            stubMatchSave();
            UUID owner = UUID.randomUUID();
            LiveMatchState state = matchService.createMatch(GameMode.BINARY_DUEL, owner);

            assertThatThrownBy(() ->
                    matchService.markReady(state.getMatchId(), UUID.randomUUID(), true))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode())
                            .isEqualTo(ErrorCode.FORBIDDEN));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // removePlayer
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("removePlayer")
    @Nested
    class Remove {

        @DisplayName("Quita al jugador y emite PLAYER_LEFT")
        @Test
        void quitaYEmite() {
            stubMatchSave();
            User u1 = user("alice");
            User u2 = user("bob");
            when(userRepository.findById(u1.getId())).thenReturn(Optional.of(u1));
            when(userRepository.findById(u2.getId())).thenReturn(Optional.of(u2));

            LiveMatchState state = matchService.createMatch(GameMode.BINARY_DUEL, u1.getId());
            matchService.addPlayer(state.getMatchId(), u1.getId());
            matchService.addPlayer(state.getMatchId(), u2.getId());
            clearInvocations(broker);

            matchService.removePlayer(state.getMatchId(), u1.getId());

            assertThat(state.getPlayers()).doesNotContainKey(u1.getId());
            ArgumentCaptor<MatchEventEnvelope> ev = ArgumentCaptor.forClass(MatchEventEnvelope.class);
            verify(broker).convertAndSend(eq("/topic/match/" + state.getMatchId()), ev.capture());
            assertThat(ev.getValue().type()).isEqualTo("PLAYER_LEFT");
        }

        @DisplayName("Quitar al último jugador elimina la partida y la marca FINISHED en BD")
        @Test
        void ultimoJugador_eliminaPartida() {
            stubMatchSave();
            User u = user("alice");
            when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));

            LiveMatchState state = matchService.createMatch(GameMode.BINARY_DUEL, u.getId());
            matchService.addPlayer(state.getMatchId(), u.getId());

            Match persisted = Match.builder()
                    .id(state.getMatchId()).mode(GameMode.BINARY_DUEL)
                    .status(MatchStatus.WAITING).roomCode(state.getRoomCode())
                    .createdAt(Instant.now()).build();
            when(matchRepository.findById(state.getMatchId())).thenReturn(Optional.of(persisted));

            matchService.removePlayer(state.getMatchId(), u.getId());

            assertThat(matchService.liveMatchesView()).doesNotContainKey(state.getMatchId());
            assertThat(persisted.getStatus()).isEqualTo(MatchStatus.FINISHED);
            assertThat(persisted.getFinishedAt()).isNotNull();
        }

        @DisplayName("Match inexistente no lanza nada (silencioso)")
        @Test
        void matchInexistente_silencioso() {
            assertThatCode(() -> matchService.removePlayer(UUID.randomUUID(), UUID.randomUUID()))
                    .doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // notifyMatchFound
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("notifyMatchFound")
    @Nested
    class NotifyMatchFound {

        @DisplayName("Envía MATCH_FOUND al canal personal del usuario")
        @Test
        void enviaCanalPersonal() {
            UUID userId = UUID.randomUUID();
            UUID matchId = UUID.randomUUID();
            var event = new MatchFoundEvent(
                    matchId, GameMode.BINARY_DUEL,
                    List.of(new PlayerInLobbyDto(UUID.randomUUID(), "rival", null, false)));

            matchService.notifyMatchFound(userId, event);

            ArgumentCaptor<MatchEventEnvelope> ev = ArgumentCaptor.forClass(MatchEventEnvelope.class);
            verify(broker).convertAndSendToUser(
                    eq(userId.toString()), eq("/queue/match"), ev.capture());
            assertThat(ev.getValue().type()).isEqualTo("MATCH_FOUND");
            assertThat(ev.getValue().matchId()).isEqualTo(matchId);
        }
    }
}
