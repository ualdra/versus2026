package com.versus.api.duel.engine;

import com.versus.api.common.exception.ApiException;
import com.versus.api.duel.dto.PlayerRoundOutcome;
import com.versus.api.duel.state.DuelMatchState;
import com.versus.api.duel.state.DuelPlayerRuntime;
import com.versus.api.duel.state.DuelRoundState;
import com.versus.api.duel.state.RawAnswer;
import com.versus.api.match.GameMode;
import com.versus.api.questions.QuestionType;
import com.versus.api.questions.domain.Question;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PrecisionDuelEngine")
class PrecisionDuelEngineTest {

    private final PrecisionDuelEngine engine = new PrecisionDuelEngine();

    private final UUID A = UUID.randomUUID();
    private final UUID B = UUID.randomUUID();
    private final UUID Q = UUID.randomUUID();

    private Question question(String correctValue) {
        return Question.builder()
                .id(Q).type(QuestionType.NUMERIC).text("?").category("g")
                .correctValue(new BigDecimal(correctValue))
                .tolerancePercent(new BigDecimal("5"))
                .build();
    }

    private DuelMatchState match() {
        DuelMatchState s = new DuelMatchState(UUID.randomUUID(), GameMode.PRECISION_DUEL);
        s.getPlayers().put(A, DuelPlayerRuntime.builder().userId(A).username("alice").livesRemaining(3).build());
        s.getPlayers().put(B, DuelPlayerRuntime.builder().userId(B).username("bob").livesRemaining(3).build());
        return s;
    }

    private DuelRoundState round() {
        return new DuelRoundState(1, Q, Instant.now(), Instant.now().plusSeconds(15));
    }

    private void answer(DuelRoundState r, UUID user, String value) {
        r.getAnswers().put(user, new RawAnswer(user, Q, null,
                value == null ? null : new BigDecimal(value), Instant.now()));
    }

    @DisplayName("metadata: PRECISION_DUEL + NUMERIC")
    @Test
    void metadata() {
        assertThat(engine.mode()).isEqualTo(GameMode.PRECISION_DUEL);
        assertThat(engine.questionType()).isEqualTo(QuestionType.NUMERIC);
    }

    @DisplayName("Camino feliz: ambos cerca, ganador con menor desviacion no pierde vida")
    @Test
    void caminoFeliz() {
        DuelMatchState s = match();
        DuelRoundState r = round();
        answer(r, A, "98");  // dev 2%
        answer(r, B, "120"); // dev 20%
        RoundResolution res = engine.resolveRound(s, r, question("100"));

        PlayerRoundOutcome ao = outcome(res, A);
        PlayerRoundOutcome bo = outcome(res, B);
        assertThat(ao.lifeDelta()).isZero();
        assertThat(ao.isCorrect()).isTrue();
        // Dano = ceil(|2 - 20| * 0.02) = ceil(0.36) = 1, pero el minimo es 1.
        assertThat(bo.lifeDelta()).isEqualTo(-1);
        assertThat(bo.isCorrect()).isFalse();
        // Score winner +100
        assertThat(s.getPlayers().get(A).getScore()).isEqualTo(100);
    }

    @DisplayName("Diferencia grande de desviacion produce dano proporcional")
    @Test
    void danoProporcional() {
        DuelMatchState s = match();
        DuelRoundState r = round();
        answer(r, A, "100");   // dev 0%
        answer(r, B, "5000");  // dev 4900%
        RoundResolution res = engine.resolveRound(s, r, question("100"));

        PlayerRoundOutcome bo = outcome(res, B);
        // Dano = ceil(4900 * 0.02) = 98
        assertThat(bo.lifeDelta()).isEqualTo(-98);
    }

    @DisplayName("Empate de desviacion: ambos quedan en 0 dano y suman racha")
    @Test
    void empateDesviacion() {
        DuelMatchState s = match();
        DuelRoundState r = round();
        answer(r, A, "110"); // dev 10%
        answer(r, B, "90");  // dev 10%
        RoundResolution res = engine.resolveRound(s, r, question("100"));

        assertThat(outcome(res, A).lifeDelta()).isZero();
        assertThat(outcome(res, B).lifeDelta()).isZero();
        assertThat(s.getPlayers().get(A).getCurrentStreak()).isEqualTo(1);
        assertThat(s.getPlayers().get(B).getCurrentStreak()).isEqualTo(1);
    }

    @DisplayName("Sin respuesta antes del deadline: -2 vidas y deviation=100% para stats")
    @Test
    void timeout() {
        DuelMatchState s = match();
        DuelRoundState r = round();
        answer(r, A, "100");
        // B no responde
        RoundResolution res = engine.resolveRound(s, r, question("100"));

        PlayerRoundOutcome bo = outcome(res, B);
        assertThat(bo.answered()).isFalse();
        assertThat(bo.lifeDelta()).isEqualTo(-2);
        // Suma el 100% al promedio del jugador (penalizacion fuerte)
        assertThat(s.getPlayers().get(B).getDeviationSum()).isEqualTo(100.0);
        assertThat(s.getPlayers().get(B).getDeviationCount()).isEqualTo(1);
    }

    @DisplayName("DeviationStats acumula sum/count para avgDeviation final")
    @Test
    void acumuladorDeviation() {
        DuelMatchState s = match();
        // Round 1: A = 2%, B = 10%
        DuelRoundState r1 = round();
        answer(r1, A, "98");
        answer(r1, B, "110");
        engine.resolveRound(s, r1, question("100"));
        // Round 2: A = 5%, B = 15%
        DuelRoundState r2 = new DuelRoundState(2, Q, Instant.now(), Instant.now().plusSeconds(15));
        answer(r2, A, "105");
        answer(r2, B, "115");
        engine.resolveRound(s, r2, question("100"));

        DuelPlayerRuntime aRt = s.getPlayers().get(A);
        assertThat(aRt.getDeviationCount()).isEqualTo(2);
        assertThat(aRt.getDeviationSum()).isEqualTo(7.0); // 2 + 5
    }

    @DisplayName("Pregunta con correctValue=0 lanza VALIDATION_ERROR")
    @Test
    void correctValueCero() {
        DuelMatchState s = match();
        DuelRoundState r = round();
        answer(r, A, "10");
        answer(r, B, "20");
        assertThatThrownBy(() -> engine.resolveRound(s, r, question("0")))
                .isInstanceOf(ApiException.class);
    }

    @DisplayName("Reveal expone correctValue, correctOptionId=null en numerico")
    @Test
    void reveal() {
        DuelMatchState s = match();
        DuelRoundState r = round();
        answer(r, A, "98");
        answer(r, B, "120");
        RoundResolution res = engine.resolveRound(s, r, question("100"));

        assertThat(res.reveal().correctValue()).isEqualByComparingTo("100");
        assertThat(res.reveal().correctOptionId()).isNull();
    }

    private static PlayerRoundOutcome outcome(RoundResolution res, UUID id) {
        return res.outcomes().stream().filter(o -> o.userId().equals(id)).findFirst().orElseThrow();
    }
}
