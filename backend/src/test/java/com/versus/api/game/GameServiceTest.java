package com.versus.api.game;

import com.versus.api.achievements.AchievementService;
import com.versus.api.cards.CardService;
import com.versus.api.cards.CardStatus;
import com.versus.api.cards.domain.Card;
import com.versus.api.common.exception.ApiException;
import com.versus.api.common.exception.ErrorCode;
import com.versus.api.game.dto.PrecisionAnswerRequest;
import com.versus.api.game.dto.PrecisionAnswerResponse;
import com.versus.api.game.dto.StartGameResponse;
import com.versus.api.game.dto.SurvivalAnswerRequest;
import com.versus.api.game.dto.SurvivalAnswerResponse;
import com.versus.api.match.GameMode;
import com.versus.api.match.MatchResult;
import com.versus.api.match.MatchStatus;
import com.versus.api.match.domain.Match;
import com.versus.api.match.domain.MatchPlayer;
import com.versus.api.match.domain.MatchPlayerId;
import com.versus.api.match.domain.MatchRound;
import com.versus.api.match.repo.MatchAnswerRepository;
import com.versus.api.match.repo.MatchPlayerRepository;
import com.versus.api.match.repo.MatchRepository;
import com.versus.api.match.repo.MatchRoundRepository;
import com.versus.api.stats.StatsService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("GameService")
@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock MatchRepository matches;
    @Mock MatchPlayerRepository matchPlayers;
    @Mock MatchRoundRepository matchRounds;
    @Mock MatchAnswerRepository matchAnswers;
    @Mock CardService cards;
    @Mock StatsService statsService;
    @Mock AchievementService achievementService;

    @InjectMocks GameService gameService;

    private static final UUID USER_ID     = UUID.fromString("aaaa0000-0000-0000-0000-000000000001");
    private static final UUID SESSION_ID  = UUID.fromString("bbbb0000-0000-0000-0000-000000000002");
    // The "questionId" sent by the client is the round token stored on MatchPlayer.
    private static final UUID QUESTION_ID = UUID.fromString("cccc0000-0000-0000-0000-000000000003");
    private static final UUID CARD_A_ID   = UUID.fromString("dddd0000-0000-0000-0000-000000000004");
    private static final UUID CARD_B_ID   = UUID.fromString("eeee0000-0000-0000-0000-000000000005");
    private static final UUID ROUND_ID    = UUID.fromString("ffff0000-0000-0000-0000-000000000006");

    // CORRECT_OPT is the winning card for a non-inverse pair (higher valor).
    private static final UUID CORRECT_OPT = CARD_A_ID;
    private static final UUID WRONG_OPT   = CARD_B_ID;

    // ── Factories ──────────────────────────────────────────────────────────

    private Card card(UUID id, BigDecimal valor, boolean inverse) {
        return Card.builder()
                .id(id)
                .categoria("sport").subcategoria("football").nombre("Card " + id)
                .valor(valor).unidad("pts").inverse(inverse).eligibleForSurvival(true)
                .status(CardStatus.ACTIVE).textHash("hash-" + id).build();
    }

    /** Non-inverse pair where CARD_A wins (higher valor). */
    private CardService.CardPair survivalPair() {
        return new CardService.CardPair(
                card(CARD_A_ID, new BigDecimal("100"), false),
                card(CARD_B_ID, new BigDecimal("50"),  false));
    }

    private Card precisionCard(BigDecimal valor) {
        return card(CARD_A_ID, valor, false);
    }

    private MatchPlayer player(int lives, int roundsPlayed, int streak) {
        return MatchPlayer.builder()
                .id(new MatchPlayerId(SESSION_ID, USER_ID))
                .livesRemaining(lives).score(0).currentStreak(streak)
                .bestStreakInMatch(streak).roundsPlayed(roundsPlayed)
                .currentCardAId(CARD_A_ID).currentCardBId(CARD_B_ID)
                .currentRoundToken(QUESTION_ID) // anti-replay: token sent by client must equal this
                .build();
    }

    private Match match(GameMode mode, MatchStatus status) {
        return Match.builder().id(SESSION_ID).mode(mode).status(status).ownerUserId(USER_ID).build();
    }

    /**
     * Stubs the minimum needed for a successful start* flow:
     *  - matches.save sets the id, matchPlayers.save echoes back.
     * Per-mode card stubbing lives in the start* sub-classes (because survival uses
     * getRandomPairForSurvival and precision uses getRandomCard).
     */
    private void stubStartCommon() {
        when(matches.save(any())).thenAnswer(inv -> { Match m = inv.getArgument(0); m.setId(SESSION_ID); return m; });
        when(matchPlayers.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    /**
     * Stubs needed by answerSurvival when the response logic reaches DB writes
     * (i.e. anti-replay + option validation pass). Card lookups always resolve.
     */
    private void stubSurvivalSession(MatchPlayer p) {
        when(matches.findById(SESSION_ID)).thenReturn(Optional.of(match(GameMode.SURVIVAL, MatchStatus.IN_PROGRESS)));
        when(matchPlayers.findById(new MatchPlayerId(SESSION_ID, USER_ID))).thenReturn(Optional.of(p));
        when(cards.getById(CARD_A_ID)).thenReturn(card(CARD_A_ID, new BigDecimal("100"), false));
        when(cards.getById(CARD_B_ID)).thenReturn(card(CARD_B_ID, new BigDecimal("50"),  false));
        // round/answer persistence — needed only when validation passes.
        lenient().when(matchRounds.countByMatchId(SESSION_ID)).thenReturn(0L);
        lenient().when(matchRounds.save(any())).thenAnswer(inv -> { MatchRound r = inv.getArgument(0); r.setId(ROUND_ID); return r; });
    }

    /**
     * For tests that throw BEFORE reaching card lookup (session guards),
     * we only need the matches.findById stub set up by the caller.
     */
    private void stubPrecisionSession(MatchPlayer p, Card c) {
        when(matches.findById(SESSION_ID)).thenReturn(Optional.of(match(GameMode.PRECISION, MatchStatus.IN_PROGRESS)));
        when(matchPlayers.findById(new MatchPlayerId(SESSION_ID, USER_ID))).thenReturn(Optional.of(p));
        when(cards.getById(CARD_A_ID)).thenReturn(c);
        lenient().when(matchRounds.countByMatchId(SESSION_ID)).thenReturn(0L);
        lenient().when(matchRounds.save(any())).thenAnswer(inv -> { MatchRound r = inv.getArgument(0); r.setId(ROUND_ID); return r; });
    }

    private void stubNextSurvivalPair() {
        when(cards.getRandomPairForSurvival()).thenReturn(survivalPair());
    }

    private void stubNextPrecisionCard() {
        when(cards.getRandomCard()).thenReturn(precisionCard(new BigDecimal("100")));
    }

    private SurvivalAnswerRequest survivalReq(UUID optionId) {
        return new SurvivalAnswerRequest(SESSION_ID, QUESTION_ID, optionId);
    }

    private PrecisionAnswerRequest precisionReq(BigDecimal value) {
        return new PrecisionAnswerRequest(SESSION_ID, QUESTION_ID, value);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // START SURVIVAL
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("startSurvival")
    @Nested
    class StartSurvival {

        @DisplayName("Devuelve sessionId y primera pregunta")
        @Test
        void caminoFeliz_devuelveSessionIdYPrimeraPregunta() {
            stubStartCommon();
            when(cards.getRandomPairForSurvival()).thenReturn(survivalPair());
            StartGameResponse res = gameService.startSurvival(USER_ID);
            assertThat(res.sessionId()).isEqualTo(SESSION_ID);
            assertThat(res.question()).isNotNull();
        }

        @DisplayName("Crea la partida con modo SURVIVAL y estado IN_PROGRESS")
        @Test
        void creaMatchConModeSurvivalYStatusInProgress() {
            stubStartCommon();
            when(cards.getRandomPairForSurvival()).thenReturn(survivalPair());
            gameService.startSurvival(USER_ID);
            ArgumentCaptor<Match> cap = ArgumentCaptor.forClass(Match.class);
            verify(matches).save(cap.capture());
            assertThat(cap.getValue().getMode()).isEqualTo(GameMode.SURVIVAL);
            assertThat(cap.getValue().getStatus()).isEqualTo(MatchStatus.IN_PROGRESS);
        }

        @DisplayName("Crea el jugador con 3 vidas")
        @Test
        void creaJugadorConTresVidas() {
            stubStartCommon();
            when(cards.getRandomPairForSurvival()).thenReturn(survivalPair());
            gameService.startSurvival(USER_ID);
            ArgumentCaptor<MatchPlayer> cap = ArgumentCaptor.forClass(MatchPlayer.class);
            // matchPlayers.save is called twice: createPlayer + after assigning cards.
            verify(matchPlayers, atLeastOnce()).save(cap.capture());
            assertThat(cap.getAllValues().get(0).getLivesRemaining()).isEqualTo(3);
        }

        @DisplayName("Solicita el primer par de cartas a CardService")
        @Test
        void solicitudPrimerParDeCartas() {
            stubStartCommon();
            when(cards.getRandomPairForSurvival()).thenReturn(survivalPair());
            gameService.startSurvival(USER_ID);
            verify(cards).getRandomPairForSurvival();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // START PRECISION
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("startPrecision")
    @Nested
    class StartPrecision {

        @DisplayName("Crea el jugador con 100 vidas")
        @Test
        void creaJugadorConCienVidas() {
            stubStartCommon();
            when(cards.getRandomCard()).thenReturn(precisionCard(new BigDecimal("100")));
            gameService.startPrecision(USER_ID);
            ArgumentCaptor<MatchPlayer> cap = ArgumentCaptor.forClass(MatchPlayer.class);
            verify(matchPlayers, atLeastOnce()).save(cap.capture());
            assertThat(cap.getAllValues().get(0).getLivesRemaining()).isEqualTo(100);
        }

        @DisplayName("Solicita una carta aleatoria a CardService")
        @Test
        void solicitudPrimeraCartaPrecision() {
            stubStartCommon();
            when(cards.getRandomCard()).thenReturn(precisionCard(new BigDecimal("100")));
            gameService.startPrecision(USER_ID);
            verify(cards).getRandomCard();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ANSWER SURVIVAL — guardas de sesión
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("answerSurvival — guardas de sesión")
    @Nested
    class AnswerSurvivalSession {

        @DisplayName("Sesión no encontrada lanza NOT_FOUND")
        @Test
        void sesionNoEncontrada_lanzaNotFound() {
            when(matches.findById(SESSION_ID)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> gameService.answerSurvival(USER_ID, survivalReq(CORRECT_OPT)))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.NOT_FOUND));
        }

        @DisplayName("Sesión de otro usuario lanza FORBIDDEN")
        @Test
        void sesionDeOtroUsuario_lanzaForbidden() {
            UUID otherUser = UUID.randomUUID();
            Match otherMatch = Match.builder().id(SESSION_ID).mode(GameMode.SURVIVAL)
                    .status(MatchStatus.IN_PROGRESS).ownerUserId(otherUser).build();
            when(matches.findById(SESSION_ID)).thenReturn(Optional.of(otherMatch));
            assertThatThrownBy(() -> gameService.answerSurvival(USER_ID, survivalReq(CORRECT_OPT)))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.FORBIDDEN));
        }

        @DisplayName("Sesión en modo incorrecto lanza VALIDATION_ERROR")
        @Test
        void sesionEnModoIncorrecto_lanzaValidation() {
            when(matches.findById(SESSION_ID)).thenReturn(
                    Optional.of(match(GameMode.PRECISION, MatchStatus.IN_PROGRESS)));
            assertThatThrownBy(() -> gameService.answerSurvival(USER_ID, survivalReq(CORRECT_OPT)))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        }

        @DisplayName("Sesión ya terminada lanza CONFLICT")
        @Test
        void sesionTerminada_lanzaConflict() {
            when(matches.findById(SESSION_ID)).thenReturn(
                    Optional.of(match(GameMode.SURVIVAL, MatchStatus.FINISHED)));
            assertThatThrownBy(() -> gameService.answerSurvival(USER_ID, survivalReq(CORRECT_OPT)))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.CONFLICT));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ANSWER SURVIVAL — anti-replay (test dedicado)
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("answerSurvival — anti-replay")
    @Nested
    class AnswerSurvivalAntiReplay {

        @DisplayName("Token de pregunta distinto al actual lanza VALIDATION_ERROR")
        @Test
        void tokenDistinto_lanzaValidation() {
            MatchPlayer p = player(3, 0, 0); // currentRoundToken = QUESTION_ID
            when(matches.findById(SESSION_ID)).thenReturn(
                    Optional.of(match(GameMode.SURVIVAL, MatchStatus.IN_PROGRESS)));
            when(matchPlayers.findById(new MatchPlayerId(SESSION_ID, USER_ID))).thenReturn(Optional.of(p));

            UUID staleToken = UUID.randomUUID();
            SurvivalAnswerRequest req = new SurvivalAnswerRequest(SESSION_ID, staleToken, CORRECT_OPT);

            assertThatThrownBy(() -> gameService.answerSurvival(USER_ID, req))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR))
                    .hasMessageContaining("anti-replay");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ANSWER SURVIVAL — lógica de puntuación y vidas
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("answerSurvival — puntuación y vidas")
    @Nested
    class AnswerSurvivalScoring {

        @DisplayName("Respuesta correcta no reduce vidas")
        @Test
        void respuestaCorrecta_noReduceVidas() {
            stubSurvivalSession(player(3, 0, 0));
            stubNextSurvivalPair();
            SurvivalAnswerResponse res = gameService.answerSurvival(USER_ID, survivalReq(CORRECT_OPT));
            assertThat(res.correct()).isTrue();
            assertThat(res.livesRemaining()).isEqualTo(3);
            assertThat(res.lifeDelta()).isEqualTo(0);
        }

        @DisplayName("Primer acierto: scoreDelta=50, streak=1")
        @Test
        void primerAcierto_score50_streak1() {
            stubSurvivalSession(player(3, 0, 0));
            stubNextSurvivalPair();
            SurvivalAnswerResponse res = gameService.answerSurvival(USER_ID, survivalReq(CORRECT_OPT));
            assertThat(res.scoreDelta()).isEqualTo(50);
            assertThat(res.streak()).isEqualTo(1);
        }

        @DisplayName("Segundo acierto consecutivo: scoreDelta=100, streak=2")
        @Test
        void dosAciertosConsecutivos_score100Adicional_streak2() {
            stubSurvivalSession(player(3, 1, 1));
            stubNextSurvivalPair();
            SurvivalAnswerResponse res = gameService.answerSurvival(USER_ID, survivalReq(CORRECT_OPT));
            assertThat(res.scoreDelta()).isEqualTo(100);
            assertThat(res.streak()).isEqualTo(2);
        }

        @DisplayName("Respuesta incorrecta reduce una vida y resetea streak")
        @Test
        void respuestaIncorrecta_reduceunaVida_resetStreak() {
            stubSurvivalSession(player(3, 0, 2));
            stubNextSurvivalPair();
            SurvivalAnswerResponse res = gameService.answerSurvival(USER_ID, survivalReq(WRONG_OPT));
            assertThat(res.correct()).isFalse();
            assertThat(res.livesRemaining()).isEqualTo(2);
            assertThat(res.lifeDelta()).isEqualTo(-1);
            assertThat(res.streak()).isEqualTo(0);
        }

        @DisplayName("Las vidas no pueden ser negativas")
        @Test
        void vidasNoSeHaceNegativas() {
            stubSurvivalSession(player(1, 4, 0));
            SurvivalAnswerResponse res = gameService.answerSurvival(USER_ID, survivalReq(WRONG_OPT));
            assertThat(res.livesRemaining()).isGreaterThanOrEqualTo(0);
        }

        @DisplayName("Opción que no pertenece a la pregunta lanza VALIDATION_ERROR")
        @Test
        void opcionNoPertenecePregunta_lanzaValidation() {
            // Validation runs AFTER cards.getById, so those stubs are needed.
            stubSurvivalSession(player(3, 0, 0));
            UUID unknownOpt = UUID.randomUUID();
            assertThatThrownBy(() -> gameService.answerSurvival(USER_ID, survivalReq(unknownOpt)))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        }

        @DisplayName("Sin game over devuelve siguiente pregunta no nula")
        @Test
        void sinGameOver_devuelveNextQuestion() {
            stubSurvivalSession(player(3, 0, 0));
            stubNextSurvivalPair();
            SurvivalAnswerResponse res = gameService.answerSurvival(USER_ID, survivalReq(CORRECT_OPT));
            assertThat(res.gameOver()).isFalse();
            assertThat(res.nextQuestion()).isNotNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ANSWER SURVIVAL — game over y condición de victoria
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("answerSurvival — game over")
    @Nested
    class AnswerSurvivalGameOver {

        @DisplayName("Última vida con respuesta incorrecta: gameOver=true, sin nextQuestion")
        @Test
        void ultimaVida_wrongAnswer_gameOver() {
            stubSurvivalSession(player(1, 4, 0));
            SurvivalAnswerResponse res = gameService.answerSurvival(USER_ID, survivalReq(WRONG_OPT));
            assertThat(res.gameOver()).isTrue();
            assertThat(res.nextQuestion()).isNull();
            assertThat(res.livesRemaining()).isEqualTo(0);
        }

        @DisplayName("Game over con 5 rondas jugadas → resultado WIN")
        @Test
        void gameOverConRoundsPlayed4_despuesDeRespuesta_esWin() {
            stubSurvivalSession(player(1, 4, 0));
            gameService.answerSurvival(USER_ID, survivalReq(WRONG_OPT));
            ArgumentCaptor<MatchPlayer> cap = ArgumentCaptor.forClass(MatchPlayer.class);
            verify(matchPlayers).save(cap.capture());
            assertThat(cap.getValue().getResult()).isEqualTo(MatchResult.WIN);
        }

        @DisplayName("Game over con 3 rondas jugadas → resultado LOSS")
        @Test
        void gameOverConRoundsPlayed2_despuesDeRespuesta_esLoss() {
            stubSurvivalSession(player(1, 2, 0));
            gameService.answerSurvival(USER_ID, survivalReq(WRONG_OPT));
            ArgumentCaptor<MatchPlayer> cap = ArgumentCaptor.forClass(MatchPlayer.class);
            verify(matchPlayers).save(cap.capture());
            assertThat(cap.getValue().getResult()).isEqualTo(MatchResult.LOSS);
        }

        @DisplayName("Game over llama a statsService con el modo correcto")
        @Test
        void gameOver_llamaStatsService() {
            stubSurvivalSession(player(1, 0, 0));
            gameService.answerSurvival(USER_ID, survivalReq(WRONG_OPT));
            verify(statsService).recordFinishedGame(eq(USER_ID), eq(GameMode.SURVIVAL), any(), isNull());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ANSWER PRECISION — validación de valor correcto
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("answerPrecision — validación de valor correcto")
    @Nested
    class AnswerPrecisionValidation {

        @DisplayName("correctValue=null lanza VALIDATION_ERROR")
        @Test
        void correctValueNull_lanzaValidation() {
            // Validation runs AFTER cards.getById; no next-question stubs needed.
            stubPrecisionSession(player(100, 0, 0), precisionCard(null));
            assertThatThrownBy(() -> gameService.answerPrecision(USER_ID, precisionReq(new BigDecimal("50"))))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        }

        @DisplayName("correctValue=0 lanza VALIDATION_ERROR (división por cero)")
        @Test
        void correctValueCero_lanzaValidation() {
            stubPrecisionSession(player(100, 0, 0), precisionCard(BigDecimal.ZERO));
            assertThatThrownBy(() -> gameService.answerPrecision(USER_ID, precisionReq(new BigDecimal("50"))))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ANSWER PRECISION — lógica de lifeDelta (tolerancia fija = 5%)
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("answerPrecision — lifeDelta según desviación")
    @Nested
    class AnswerPrecisionLifeDelta {

        @DisplayName("Desviación ≤ tolerancia (2%) → lifeDelta=+5, correct=true")
        @Test
        void dentroTolerancia_lifeDeltaPositivo() {
            stubPrecisionSession(player(50, 0, 0), precisionCard(new BigDecimal("100")));
            stubNextPrecisionCard();
            PrecisionAnswerResponse res = gameService.answerPrecision(USER_ID,
                    precisionReq(new BigDecimal("102")));
            assertThat(res.lifeDelta()).isEqualTo(5);
            assertThat(res.gameOver()).isFalse();
        }

        @DisplayName("Desviación entre tolerancia y 2× tolerancia (7%) → lifeDelta=0")
        @Test
        void entreToleranciaYDoble_lifeDeltaCero() {
            stubPrecisionSession(player(50, 0, 0), precisionCard(new BigDecimal("100")));
            stubNextPrecisionCard();
            PrecisionAnswerResponse res = gameService.answerPrecision(USER_ID,
                    precisionReq(new BigDecimal("107")));
            assertThat(res.lifeDelta()).isEqualTo(0);
        }

        @DisplayName("Desviación > 2× tolerancia (20%) → lifeDelta negativo")
        @Test
        void mayorDobleTolerancia_lifeDeltaNegativo() {
            stubPrecisionSession(player(50, 0, 0), precisionCard(new BigDecimal("100")));
            stubNextPrecisionCard();
            PrecisionAnswerResponse res = gameService.answerPrecision(USER_ID,
                    precisionReq(new BigDecimal("120")));
            assertThat(res.lifeDelta()).isEqualTo(-20);
        }

        @DisplayName("Penalización acotada a -50 aunque la desviación sea enorme")
        @Test
        void penalizacionAcotadaA50() {
            stubPrecisionSession(player(100, 0, 0), precisionCard(new BigDecimal("100")));
            stubNextPrecisionCard();
            PrecisionAnswerResponse res = gameService.answerPrecision(USER_ID,
                    precisionReq(new BigDecimal("1100")));
            assertThat(res.lifeDelta()).isEqualTo(-50);
        }

        @DisplayName("Game over precision: vidas a 0, gameOver=true")
        @Test
        void vidasAZero_gameOver() {
            stubPrecisionSession(player(5, 0, 0), precisionCard(new BigDecimal("100")));
            PrecisionAnswerResponse res = gameService.answerPrecision(USER_ID,
                    precisionReq(new BigDecimal("1100")));
            assertThat(res.gameOver()).isTrue();
            assertThat(res.livesRemaining()).isEqualTo(0);
            assertThat(res.nextQuestion()).isNull();
        }
    }
}
