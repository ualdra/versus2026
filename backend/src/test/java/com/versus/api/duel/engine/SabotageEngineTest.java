package com.versus.api.duel.engine;

import com.versus.api.duel.state.DuelMatchState;
import com.versus.api.duel.state.DuelPlayerRuntime;
import com.versus.api.duel.state.DuelRoundState;
import com.versus.api.duel.state.RawAnswer;
import com.versus.api.duel.state.SabotageType;
import com.versus.api.match.GameMode;
import com.versus.api.questions.QuestionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SabotageEngine")
class SabotageEngineTest {

    private final SabotageEngine engine = new SabotageEngine();

    private final UUID A = UUID.randomUUID();
    private final UUID B = UUID.randomUUID();
    private final UUID Q = UUID.randomUUID();
    private final UUID CORRECT_OPT = UUID.randomUUID();
    private final UUID WRONG_OPT   = UUID.randomUUID();

    /** CORRECT_OPT tiene valor 10 (mayor), WRONG_OPT tiene valor 5; inverse=false → gana el mayor */
    private CardRoundContext context() {
        return new CardRoundContext(
                CORRECT_OPT, WRONG_OPT,
                new BigDecimal("10"), new BigDecimal("5"),
                false, null);
    }

    private DuelMatchState match() {
        DuelMatchState s = new DuelMatchState(UUID.randomUUID(), GameMode.SABOTAGE);
        s.getPlayers().put(A, DuelPlayerRuntime.builder().userId(A).username("alice").livesRemaining(3).build());
        s.getPlayers().put(B, DuelPlayerRuntime.builder().userId(B).username("bob").livesRemaining(3).build());
        return s;
    }

    private DuelRoundState round() {
        return new DuelRoundState(1, Q, Instant.now(), Instant.now().plusSeconds(15));
    }

    private void answer(DuelRoundState r, UUID user, UUID option) {
        r.getAnswers().put(user, new RawAnswer(user, Q, option == null ? null : option.toString(),
                null, Instant.now()));
    }

    @DisplayName("metadata: SABOTAGE + BINARY")
    @Test
    void metadata() {
        assertThat(engine.mode()).isEqualTo(GameMode.SABOTAGE);
        assertThat(engine.questionType()).isEqualTo(QuestionType.BINARY);
    }

    @DisplayName("Token earning: 3 aciertos consecutivos → +1 token")
    @Test
    void tokenEarning() {
        DuelMatchState s = match();
        for (int i = 1; i <= 3; i++) {
            DuelRoundState r = new DuelRoundState(i, Q, Instant.now(), Instant.now().plusSeconds(15));
            answer(r, A, CORRECT_OPT);
            answer(r, B, WRONG_OPT);
            engine.resolveRound(s, r, context());
        }
        assertThat(s.getPlayers().get(A).getSabotageTokens()).isEqualTo(1);
        assertThat(s.getPlayers().get(A).getCurrentStreak()).isEqualTo(3);
    }

    @DisplayName("6 aciertos consecutivos → +2 tokens (uno por cada multiplo de 3)")
    @Test
    void tokenEarningSeisAciertos() {
        DuelMatchState s = match();
        for (int i = 1; i <= 6; i++) {
            DuelRoundState r = new DuelRoundState(i, Q, Instant.now(), Instant.now().plusSeconds(15));
            answer(r, A, CORRECT_OPT);
            answer(r, B, WRONG_OPT);
            engine.resolveRound(s, r, context());
        }
        assertThat(s.getPlayers().get(A).getSabotageTokens()).isEqualTo(2);
    }

    @DisplayName("Reset de racha al fallar elimina progreso hacia siguiente token")
    @Test
    void tokenResetAlFallar() {
        DuelMatchState s = match();
        for (int i = 1; i <= 2; i++) {
            DuelRoundState r = new DuelRoundState(i, Q, Instant.now(), Instant.now().plusSeconds(15));
            answer(r, A, CORRECT_OPT);
            answer(r, B, WRONG_OPT);
            engine.resolveRound(s, r, context());
        }
        assertThat(s.getPlayers().get(A).getCurrentStreak()).isEqualTo(2);
        assertThat(s.getPlayers().get(A).getSabotageTokens()).isZero();
        DuelRoundState r3 = new DuelRoundState(3, Q, Instant.now(), Instant.now().plusSeconds(15));
        answer(r3, A, WRONG_OPT);
        answer(r3, B, CORRECT_OPT);
        engine.resolveRound(s, r3, context());
        assertThat(s.getPlayers().get(A).getCurrentStreak()).isZero();
        assertThat(s.getPlayers().get(A).getSabotageTokens()).isZero();
    }

    @DisplayName("LIFE_STEAL: si target falla, atacante recupera +1 vida (con cap en 3)")
    @Test
    void lifeStealRecuperaVida() {
        DuelMatchState s = match();
        s.getPlayers().get(A).setLivesRemaining(1);
        DuelRoundState r = round();
        r.getEffectsApplied().put(A, SabotageType.LIFE_STEAL);
        answer(r, A, WRONG_OPT);
        answer(r, B, CORRECT_OPT);
        engine.resolveRound(s, r, context());
        assertThat(s.getPlayers().get(B).getLivesRemaining()).isEqualTo(3);
        var outcomes = engine.resolveRound(s, round(), context()).outcomes();
        assertThat(outcomes).isNotEmpty();
    }

    @DisplayName("LIFE_STEAL solo aplica si target falla; si acierta no roba vida")
    @Test
    void lifeStealNoAplicaSiAcierta() {
        DuelMatchState s = match();
        s.getPlayers().get(B).setLivesRemaining(2);
        DuelRoundState r = round();
        r.getEffectsApplied().put(A, SabotageType.LIFE_STEAL);
        answer(r, A, CORRECT_OPT);
        answer(r, B, WRONG_OPT);
        engine.resolveRound(s, r, context());
        assertThat(s.getPlayers().get(B).getLivesRemaining()).isEqualTo(2);
    }

    @DisplayName("LIFE_STEAL respeta el cap de vidas (MAX_LIVES=3)")
    @Test
    void lifeStealRespetaCap() {
        DuelMatchState s = match();
        DuelRoundState r = round();
        r.getEffectsApplied().put(A, SabotageType.LIFE_STEAL);
        answer(r, A, WRONG_OPT);
        answer(r, B, CORRECT_OPT);
        engine.resolveRound(s, r, context());
        assertThat(s.getPlayers().get(B).getLivesRemaining()).isEqualTo(3);
    }

    @DisplayName("Mecanica binaria base sigue funcionando: bonus de racha del rival")
    @Test
    void bonusDeRachaSeMantiene() {
        DuelMatchState s = match();
        s.getPlayers().get(A).setCurrentStreak(2);
        DuelRoundState r = round();
        answer(r, A, CORRECT_OPT);
        answer(r, B, WRONG_OPT);
        var outcomes = engine.resolveRound(s, r, context()).outcomes();
        var bOutcome = outcomes.stream().filter(o -> o.userId().equals(B)).findFirst().orElseThrow();
        assertThat(bOutcome.lifeDelta()).isEqualTo(-2);
    }

    @DisplayName("tokenThreshold() expone el threshold (3) para clientes externos")
    @Test
    void tokenThresholdAccesor() {
        assertThat(SabotageEngine.tokenThreshold()).isEqualTo(3);
    }
}
