package com.versus.api.game;

import com.versus.api.achievements.AchievementService;
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
import com.versus.api.questions.QuestionService;
import com.versus.api.questions.QuestionStatus;
import com.versus.api.questions.QuestionType;
import com.versus.api.questions.domain.Question;
import com.versus.api.questions.domain.QuestionOption;
import com.versus.api.questions.dto.QuestionBinaryResponse;
import com.versus.api.questions.dto.QuestionResponse;
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
import java.util.List;
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
    @Mock QuestionService questions;
    @Mock StatsService statsService;
    @Mock AchievementService achievementService;

    @InjectMocks GameService gameService;

    private static final UUID USER_ID     = UUID.fromString("aaaa0000-0000-0000-0000-000000000001");
    private static final UUID SESSION_ID  = UUID.fromString("bbbb0000-0000-0000-0000-000000000002");
    private static final UUID QUESTION_ID = UUID.fromString("cccc0000-0000-0000-0000-000000000003");
    private static final UUID CORRECT_OPT = UUID.fromString("dddd0000-0000-0000-0000-000000000004");
    private static final UUID WRONG_OPT   = UUID.fromString("eeee0000-0000-0000-0000-000000000005");
    private static final UUID ROUND_ID    = UUID.fromString("ffff0000-0000-0000-0000-000000000006");

    private static final QuestionResponse STUB_RESPONSE = new QuestionBinaryResponse(
            QUESTION_ID, QuestionType.BINARY, "Test?", "sport", List.of(), null);

    // ── Factories ──────────────────────────────────────────────────────────

    private Question binaryQuestion() {
        QuestionOption correct = QuestionOption.builder().id(CORRECT_OPT).text("Yes").isCorrect(true).build();
        QuestionOption wrong   = QuestionOption.builder().id(WRONG_OPT).text("No").isCorrect(false).build();
        return Question.builder().id(QUESTION_ID).type(QuestionType.BINARY).status(QuestionStatus.ACTIVE)
                .text("Test?").options(List.of(correct, wrong)).build();
    }

    private Question numericQuestion(BigDecimal correct, BigDecimal tolerance) {
        return Question.builder().id(QUESTION_ID).type(QuestionType.NUMERIC).status(QuestionStatus.ACTIVE)
                .text("How many?").correctValue(correct).tolerancePercent(tolerance).build();
    }

    private MatchPlayer player(int lives, int roundsPlayed, int streak) {
        return MatchPlayer.builder()
                .id(new MatchPlayerId(SESSION_ID, USER_ID))
                .livesRemaining(lives).score(0).currentStreak(streak)
                .bestStreakInMatch(streak).roundsPlayed(roundsPlayed).build();
    }

    private Match match(GameMode mode, MatchStatus status) {
        return Match.builder().id(SESSION_ID).mode(mode).status(status).ownerUserId(USER_ID).build();
    }

    private void stubStart() {
        when(matches.save(any())).thenAnswer(inv -> { Match m = inv.getArgument(0); m.setId(SESSION_ID); return m; });
        when(matchPlayers.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(questions.findRandomActiveQuestion(any(), any())).thenReturn(binaryQuestion());
        when(questions.toResponse(any())).thenReturn(STUB_RESPONSE);
    }

    private void stubSurvivalSession(MatchPlayer p) {
        when(matches.findById(SESSION_ID)).thenReturn(Optional.of(match(GameMode.SURVIVAL, MatchStatus.IN_PROGRESS)));
        when(matchPlayers.findById(new MatchPlayerId(SESSION_ID, USER_ID))).thenReturn(Optional.of(p));
        lenient().when(matchRounds.countByMatchId(SESSION_ID)).thenReturn(0L);
        lenient().when(matchRounds.save(any())).thenAnswer(inv -> { MatchRound r = inv.getArgument(0); r.setId(ROUND_ID); return r; });
        when(questions.findActiveQuestion(QUESTION_ID, QuestionType.BINARY)).thenReturn(binaryQuestion());
    }

    private void stubPrecisionSession(MatchPlayer p, Question q) {
        when(matches.findById(SESSION_ID)).thenReturn(Optional.of(match(GameMode.PRECISION, MatchStatus.IN_PROGRESS)));
        when(matchPlayers.findById(new MatchPlayerId(SESSION_ID, USER_ID))).thenReturn(Optional.of(p));
        lenient().when(matchRounds.countByMatchId(SESSION_ID)).thenReturn(0L);
        lenient().when(matchRounds.save(any())).thenAnswer(inv -> { MatchRound r = inv.getArgument(0); r.setId(ROUND_ID); return r; });
        when(questions.findActiveQuestion(QUESTION_ID, QuestionType.NUMERIC)).thenReturn(q);
    }

    private void stubNextSurvivalQuestion() {
        when(questions.findRandomActiveQuestion(QuestionType.BINARY, null)).thenReturn(binaryQuestion());
        when(questions.toResponse(any())).thenReturn(STUB_RESPONSE);
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
            stubStart();
            StartGameResponse res = gameService.startSurvival(USER_ID);
            assertThat(res.sessionId()).isEqualTo(SESSION_ID);
            assertThat(res.question()).isNotNull();
        }

        @DisplayName("Crea la partida con modo SURVIVAL y estado IN_PROGRESS")
        @Test
        void creaMatchConModeSurvivalYStatusInProgress() {
            stubStart();
            gameService.startSurvival(USER_ID);
            ArgumentCaptor<Match> cap = ArgumentCaptor.forClass(Match.class);
            verify(matches).save(cap.capture());
            assertThat(cap.getValue().getMode()).isEqualTo(GameMode.SURVIVAL);
            assertThat(cap.getValue().getStatus()).isEqualTo(MatchStatus.IN_PROGRESS);
        }

        @DisplayName("Crea el jugador con 3 vidas")
        @Test
        void creaJugadorConTresVidas() {
            stubStart();
            gameService.startSurvival(USER_ID);
            ArgumentCaptor<MatchPlayer> cap = ArgumentCaptor.forClass(MatchPlayer.class);
            verify(matchPlayers).save(cap.capture());
            assertThat(cap.getValue().getLivesRemaining()).isEqualTo(3);
        }

        @DisplayName("Solicita primera pregunta de tipo BINARY")
        @Test
        void solicitudPrimeraPreguntaEsTipoBinary() {
            stubStart();
            gameService.startSurvival(USER_ID);
            verify(questions).findRandomActiveQuestion(QuestionType.BINARY, null);
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
            stubStart();
            gameService.startPrecision(USER_ID);
            ArgumentCaptor<MatchPlayer> cap = ArgumentCaptor.forClass(MatchPlayer.class);
            verify(matchPlayers).save(cap.capture());
            assertThat(cap.getValue().getLivesRemaining()).isEqualTo(100);
        }

        @DisplayName("Solicita primera pregunta de tipo NUMERIC")
        @Test
        void solicitudPrimeraPreguntaEsTipoNumeric() {
            stubStart();
            gameService.startPrecision(USER_ID);
            verify(questions).findRandomActiveQuestion(QuestionType.NUMERIC, null);
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
    // ANSWER SURVIVAL — lógica de puntuación y vidas
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("answerSurvival — puntuación y vidas")
    @Nested
    class AnswerSurvivalScoring {

        @DisplayName("Respuesta correcta no reduce vidas")
        @Test
        void respuestaCorrecta_noReduceVidas() {
            stubSurvivalSession(player(3, 0, 0));
            stubNextSurvivalQuestion();
            SurvivalAnswerResponse res = gameService.answerSurvival(USER_ID, survivalReq(CORRECT_OPT));
            assertThat(res.correct()).isTrue();
            assertThat(res.livesRemaining()).isEqualTo(3);
            assertThat(res.lifeDelta()).isEqualTo(0);
        }

        @DisplayName("Primer acierto: scoreDelta=50, streak=1")
        @Test
        void primerAcierto_score50_streak1() {
            stubSurvivalSession(player(3, 0, 0));
            stubNextSurvivalQuestion();
            SurvivalAnswerResponse res = gameService.answerSurvival(USER_ID, survivalReq(CORRECT_OPT));
            assertThat(res.scoreDelta()).isEqualTo(50);
            assertThat(res.streak()).isEqualTo(1);
        }

        @DisplayName("Segundo acierto consecutivo: scoreDelta=100, streak=2")
        @Test
        void dosAciertosConsecutivos_score100Adicional_streak2() {
            stubSurvivalSession(player(3, 1, 1)); // streak ya en 1
            stubNextSurvivalQuestion();
            SurvivalAnswerResponse res = gameService.answerSurvival(USER_ID, survivalReq(CORRECT_OPT));
            assertThat(res.scoreDelta()).isEqualTo(100); // 2 * 50
            assertThat(res.streak()).isEqualTo(2);
        }

        @DisplayName("Respuesta incorrecta reduce una vida y resetea streak")
        @Test
        void respuestaIncorrecta_reduceunaVida_resetStreak() {
            stubSurvivalSession(player(3, 0, 2)); // streak en 2
            stubNextSurvivalQuestion();
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
            stubNextSurvivalQuestion();
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
            // roundsPlayed=4; la respuesta incrementa a 5, cumple >= 5 → WIN
            stubSurvivalSession(player(1, 4, 0));
            gameService.answerSurvival(USER_ID, survivalReq(WRONG_OPT));
            ArgumentCaptor<MatchPlayer> cap = ArgumentCaptor.forClass(MatchPlayer.class);
            verify(matchPlayers).save(cap.capture());
            assertThat(cap.getValue().getResult()).isEqualTo(MatchResult.WIN);
        }

        @DisplayName("Game over con 3 rondas jugadas → resultado LOSS")
        @Test
        void gameOverConRoundsPlayed2_despuesDeRespuesta_esLoss() {
            // roundsPlayed=2; la respuesta incrementa a 3, no cumple >= 5 → LOSS
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
    // ANSWER PRECISION — validación de pregunta
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("answerPrecision — validación de valor correcto")
    @Nested
    class AnswerPrecisionValidation {

        @DisplayName("correctValue=null lanza VALIDATION_ERROR")
        @Test
        void correctValueNull_lanzaValidation() {
            stubPrecisionSession(player(100, 0, 0), numericQuestion(null, new BigDecimal("5")));
            assertThatThrownBy(() -> gameService.answerPrecision(USER_ID, precisionReq(new BigDecimal("50"))))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        }

        @DisplayName("correctValue=0 lanza VALIDATION_ERROR (división por cero)")
        @Test
        void correctValueCero_lanzaValidation() {
            stubPrecisionSession(player(100, 0, 0), numericQuestion(BigDecimal.ZERO, new BigDecimal("5")));
            assertThatThrownBy(() -> gameService.answerPrecision(USER_ID, precisionReq(new BigDecimal("50"))))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ANSWER PRECISION — lógica de lifeDelta
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("answerPrecision — lifeDelta según desviación")
    @Nested
    class AnswerPrecisionLifeDelta {

        // Tolerancia = 5%. Valor correcto = 100.

        @DisplayName("Desviación ≤ tolerancia (2%) → lifeDelta=+5, correct=true")
        @Test
        void dentroTolerancia_lifeDeltaPositivo() {
            stubPrecisionSession(player(50, 0, 0),
                    numericQuestion(new BigDecimal("100"), new BigDecimal("5")));
            stubNextPrecisionQuestion();
            PrecisionAnswerResponse res = gameService.answerPrecision(USER_ID,
                    precisionReq(new BigDecimal("102"))); // 2% de desviación
            assertThat(res.lifeDelta()).isEqualTo(5);
            assertThat(res.gameOver()).isFalse();
        }

        @DisplayName("Desviación entre tolerancia y 2× tolerancia (7%) → lifeDelta=0")
        @Test
        void entreToleranciaYDoble_lifeDeltaCero() {
            stubPrecisionSession(player(50, 0, 0),
                    numericQuestion(new BigDecimal("100"), new BigDecimal("5")));
            stubNextPrecisionQuestion();
            PrecisionAnswerResponse res = gameService.answerPrecision(USER_ID,
                    precisionReq(new BigDecimal("107"))); // 7% de desviación
            assertThat(res.lifeDelta()).isEqualTo(0);
        }

        @DisplayName("Desviación > 2× tolerancia (20%) → lifeDelta negativo")
        @Test
        void mayorDobleTolerancia_lifeDeltaNegativo() {
            stubPrecisionSession(player(50, 0, 0),
                    numericQuestion(new BigDecimal("100"), new BigDecimal("5")));
            stubNextPrecisionQuestion();
            PrecisionAnswerResponse res = gameService.answerPrecision(USER_ID,
                    precisionReq(new BigDecimal("120"))); // 20% de desviación
            assertThat(res.lifeDelta()).isEqualTo(-20);
        }

        @DisplayName("Penalización acotada a -50 aunque la desviación sea enorme")
        @Test
        void penalizacionAcotadaA50() {
            stubPrecisionSession(player(100, 0, 0),
                    numericQuestion(new BigDecimal("100"), new BigDecimal("5")));
            stubNextPrecisionQuestion();
            PrecisionAnswerResponse res = gameService.answerPrecision(USER_ID,
                    precisionReq(new BigDecimal("1100"))); // 1000% de desviación
            assertThat(res.lifeDelta()).isEqualTo(-50);
        }

        @DisplayName("tolerancePercent=null usa el 5% por defecto")
        @Test
        void toleranciaNula_usaDefaultCincoPorciento() {
            // correctValue=100, respuesta=102 (2%) → dentro del 5% por defecto → +5
            stubPrecisionSession(player(50, 0, 0),
                    numericQuestion(new BigDecimal("100"), null));
            stubNextPrecisionQuestion();
            PrecisionAnswerResponse res = gameService.answerPrecision(USER_ID,
                    precisionReq(new BigDecimal("102")));
            assertThat(res.lifeDelta()).isEqualTo(5);
        }

        @DisplayName("Game over precision: vidas a 0, gameOver=true")
        @Test
        void vidasAZero_gameOver() {
            // Jugador con 5 vidas; penalización de -50 las lleva a 0
            stubPrecisionSession(player(5, 0, 0),
                    numericQuestion(new BigDecimal("100"), new BigDecimal("5")));
            PrecisionAnswerResponse res = gameService.answerPrecision(USER_ID,
                    precisionReq(new BigDecimal("1100"))); // -50
            assertThat(res.gameOver()).isTrue();
            assertThat(res.livesRemaining()).isEqualTo(0);
            assertThat(res.nextQuestion()).isNull();
        }
    }

    private void stubNextPrecisionQuestion() {
        when(questions.findRandomActiveQuestion(QuestionType.NUMERIC, null)).thenReturn(
                numericQuestion(new BigDecimal("100"), new BigDecimal("5")));
        when(questions.toResponse(any())).thenReturn(STUB_RESPONSE);
    }
}
