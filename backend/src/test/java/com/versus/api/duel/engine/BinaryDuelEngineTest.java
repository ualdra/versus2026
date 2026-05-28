package com.versus.api.duel.engine;

import com.versus.api.duel.dto.PlayerRoundOutcome;
import com.versus.api.duel.state.DuelMatchState;
import com.versus.api.duel.state.DuelPlayerRuntime;
import com.versus.api.duel.state.DuelRoundState;
import com.versus.api.duel.state.RawAnswer;
import com.versus.api.match.GameMode;
import com.versus.api.questions.QuestionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BinaryDuelEngine")
class BinaryDuelEngineTest {

    private final BinaryDuelEngine engine = new BinaryDuelEngine();

    private final UUID A = UUID.randomUUID();
    private final UUID B = UUID.randomUUID();
    private final UUID Q = UUID.randomUUID();
    // CORRECT_OPT = card con mayor valor; WRONG_OPT = card con menor valor (inverse=false)
    private final UUID CORRECT_OPT = UUID.randomUUID();
    private final UUID WRONG_OPT   = UUID.randomUUID();

    /** Context binario: CORRECT_OPT tiene valor 10, WRONG_OPT tiene valor 5; inverse=false → gana el mayor */
    private CardRoundContext context() {
        return new CardRoundContext(
                CORRECT_OPT, WRONG_OPT,
                new BigDecimal("10"), new BigDecimal("5"),
                false, null);
    }

    /** Context binario sin correcta identificable (ambos con mismo valor → correctOptionId varía pero no es null) */
    private CardRoundContext contextSinCorrecta() {
        // Para simular "sin opción correcta", pasamos context con cardBId=null
        return new CardRoundContext(WRONG_OPT, null, new BigDecimal("5"), null, false, null);
    }

    private DuelMatchState matchWith(int aLives, int aStreak, int bLives, int bStreak) {
        DuelMatchState s = new DuelMatchState(UUID.randomUUID(), GameMode.BINARY_DUEL);
        s.getPlayers().put(A, DuelPlayerRuntime.builder().userId(A).username("alice")
                .livesRemaining(aLives).currentStreak(aStreak).bestStreakInMatch(aStreak).build());
        s.getPlayers().put(B, DuelPlayerRuntime.builder().userId(B).username("bob")
                .livesRemaining(bLives).currentStreak(bStreak).bestStreakInMatch(bStreak).build());
        return s;
    }

    private DuelRoundState round() {
        return new DuelRoundState(1, Q, Instant.now(), Instant.now().plusSeconds(15));
    }

    private void answer(DuelRoundState r, UUID user, UUID option) {
        r.getAnswers().put(user, new RawAnswer(user, Q, option == null ? null : option.toString(),
                null, Instant.now()));
    }

    @DisplayName("metadata: modo BINARY_DUEL y preguntas BINARY")
    @Test
    void metadata() {
        assertThat(engine.mode()).isEqualTo(GameMode.BINARY_DUEL);
        assertThat(engine.questionType()).isEqualTo(QuestionType.BINARY);
    }

    @DisplayName("Camino feliz: ambos aciertan → 0 daño, streak+1 ambos")
    @Test
    void ambosAciertan() {
        DuelMatchState s = matchWith(3, 0, 3, 0);
        DuelRoundState r = round();
        answer(r, A, CORRECT_OPT);
        answer(r, B, CORRECT_OPT);

        RoundResolution res = engine.resolveRound(s, r, context());

        assertThat(res.outcomes()).extracting(PlayerRoundOutcome::lifeDelta).containsExactly(0, 0);
        assertThat(res.outcomes()).extracting(PlayerRoundOutcome::isCorrect).containsExactly(true, true);
        assertThat(s.getPlayers().get(A).getCurrentStreak()).isEqualTo(1);
        assertThat(s.getPlayers().get(B).getCurrentStreak()).isEqualTo(1);
    }

    @DisplayName("A acierta sin streak previo, B falla → B pierde 1 vida (sin bonus)")
    @Test
    void unoFallaSinBonus() {
        DuelMatchState s = matchWith(3, 0, 3, 0);
        DuelRoundState r = round();
        answer(r, A, CORRECT_OPT);
        answer(r, B, WRONG_OPT);

        RoundResolution res = engine.resolveRound(s, r, context());

        assertThat(outcomeOf(res, A).lifeDelta()).isEqualTo(0);
        assertThat(outcomeOf(res, B).lifeDelta()).isEqualTo(-1);
    }

    @DisplayName("A acierta con streak previo ≥1, B falla → bonus de racha → B pierde 2 vidas")
    @Test
    void bonusDeRacha() {
        DuelMatchState s = matchWith(3, 2, 3, 0);
        DuelRoundState r = round();
        answer(r, A, CORRECT_OPT);
        answer(r, B, WRONG_OPT);

        RoundResolution res = engine.resolveRound(s, r, context());

        assertThat(outcomeOf(res, A).lifeDelta()).isEqualTo(0);
        assertThat(outcomeOf(res, B).lifeDelta()).isEqualTo(-2);
        assertThat(s.getPlayers().get(A).getCurrentStreak()).isEqualTo(3);
        assertThat(s.getPlayers().get(B).getCurrentStreak()).isZero();
    }

    @DisplayName("Ambos fallan → ambos pierden 1 (sin bonus para nadie)")
    @Test
    void ambosFallan() {
        DuelMatchState s = matchWith(3, 0, 3, 0);
        DuelRoundState r = round();
        answer(r, A, WRONG_OPT);
        answer(r, B, WRONG_OPT);

        RoundResolution res = engine.resolveRound(s, r, context());

        assertThat(outcomeOf(res, A).lifeDelta()).isEqualTo(-1);
        assertThat(outcomeOf(res, B).lifeDelta()).isEqualTo(-1);
    }

    @DisplayName("Jugador no responde antes del deadline → -1 vida y se marca answered=false")
    @Test
    void timeoutSinRespuesta() {
        DuelMatchState s = matchWith(3, 4, 3, 0);
        DuelRoundState r = round();
        answer(r, A, CORRECT_OPT); // B no responde

        RoundResolution res = engine.resolveRound(s, r, context());

        assertThat(outcomeOf(res, A).lifeDelta()).isEqualTo(0);
        PlayerRoundOutcome bOutcome = outcomeOf(res, B);
        assertThat(bOutcome.answered()).isFalse();
        assertThat(bOutcome.isCorrect()).isNull();
        assertThat(bOutcome.lifeDelta()).isEqualTo(-1);
    }

    @DisplayName("Score acumula racha * 50 al acertar (misma fórmula que survival)")
    @Test
    void scoreScaling() {
        DuelMatchState s = matchWith(3, 0, 3, 0);
        DuelRoundState r1 = round();
        answer(r1, A, CORRECT_OPT);
        answer(r1, B, WRONG_OPT);
        engine.resolveRound(s, r1, context());

        DuelRoundState r2 = new DuelRoundState(2, Q, Instant.now(), Instant.now().plusSeconds(15));
        answer(r2, A, CORRECT_OPT);
        answer(r2, B, WRONG_OPT);
        engine.resolveRound(s, r2, context());

        assertThat(s.getPlayers().get(A).getCurrentStreak()).isEqualTo(2);
        assertThat(s.getPlayers().get(A).getScore()).isEqualTo(150);
    }

    @DisplayName("Reveal contiene correctOptionId (correctValue=null en binario)")
    @Test
    void revealCorrectOption() {
        DuelMatchState s = matchWith(3, 0, 3, 0);
        DuelRoundState r = round();
        answer(r, A, CORRECT_OPT);
        answer(r, B, WRONG_OPT);

        RoundResolution res = engine.resolveRound(s, r, context());

        assertThat(res.reveal().correctOptionId()).isEqualTo(CORRECT_OPT);
        assertThat(res.reveal().correctValue()).isNull();
    }

    @Nested
    @DisplayName("Comportamiento defensivo")
    class Defensive {

        @DisplayName("Context sin cardB (cardBId=null) → correctOptionId null, todos fallan")
        @Test
        void contextSinCardB_correctOptionIdNull() {
            DuelMatchState s = matchWith(3, 0, 3, 0);
            DuelRoundState r = round();
            answer(r, A, WRONG_OPT);
            answer(r, B, WRONG_OPT);

            RoundResolution res = engine.resolveRound(s, r, contextSinCorrecta());

            assertThat(res.outcomes()).extracting(PlayerRoundOutcome::isCorrect).containsExactly(false, false);
            assertThat(res.outcomes()).extracting(PlayerRoundOutcome::lifeDelta).containsExactly(-1, -1);
            assertThat(res.reveal().correctOptionId()).isNull();
        }
    }

    private static PlayerRoundOutcome outcomeOf(RoundResolution res, UUID userId) {
        return res.outcomes().stream().filter(o -> o.userId().equals(userId)).findFirst().orElseThrow();
    }

    @SuppressWarnings("unused")
    private static List<PlayerRoundOutcome> all(RoundResolution res) {
        return res.outcomes();
    }
}
