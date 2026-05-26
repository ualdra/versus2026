package com.versus.api.duel.engine;

import com.versus.api.duel.dto.PlayerRoundOutcome;
import com.versus.api.duel.dto.RoundResultPayload;
import com.versus.api.duel.state.DuelMatchState;
import com.versus.api.duel.state.DuelPlayerRuntime;
import com.versus.api.duel.state.DuelRoundState;
import com.versus.api.duel.state.RawAnswer;
import com.versus.api.match.GameMode;
import com.versus.api.questions.QuestionType;
import com.versus.api.questions.domain.Question;
import com.versus.api.questions.domain.QuestionOption;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Engine de Binary Duel (#91): pregunta binaria compartida; respuesta incorrecta = -1 vida.
 * Bonus de racha: si yo acierto y tenia racha >= 1 previa, mi rival recibe -1 vida adicional.
 *
 * El engine NO toca livesRemaining ni persiste — devuelve {@link PlayerRoundOutcome#lifeDelta()}
 * para que el orchestrator aplique el delta y broadcastee el resultado. SI actualiza
 * `score`, `currentStreak` y `bestStreakInMatch` (estado puramente del jugador, no afecta
 * a flujo externo).
 */
@Component
public class BinaryDuelEngine implements DuelEngine {

    @Override
    public GameMode mode() {
        return GameMode.BINARY_DUEL;
    }

    @Override
    public QuestionType questionType() {
        return QuestionType.BINARY;
    }

    @Override
    public RoundResolution resolveRound(DuelMatchState state, DuelRoundState round, Question question) {
        UUID correctOptionId = correctOptionId(question);
        // Snapshot pre-resolution para evaluar racha-bonus contra el streak previo (no el post-incremento).
        Map<UUID, Integer> streakBefore = new HashMap<>();
        Map<UUID, Boolean> correctness = new HashMap<>();
        state.getPlayers().forEach((uid, rt) -> streakBefore.put(uid, rt.getCurrentStreak()));

        // Primera pasada: determinar si cada jugador acerto y actualizar su propio score/streak.
        for (DuelPlayerRuntime rt : state.getPlayers().values()) {
            RawAnswer raw = round.getAnswers().get(rt.getUserId());
            if (raw == null) {
                correctness.put(rt.getUserId(), null);
                rt.setCurrentStreak(0);
                continue;
            }
            boolean correct = correctOptionId != null
                    && raw.optionId() != null
                    && correctOptionId.toString().equals(raw.optionId());
            correctness.put(rt.getUserId(), correct);
            if (correct) {
                rt.setCurrentStreak(rt.getCurrentStreak() + 1);
                rt.setBestStreakInMatch(Math.max(rt.getBestStreakInMatch(), rt.getCurrentStreak()));
                rt.setScore(rt.getScore() + rt.getCurrentStreak() * 50);
            } else {
                rt.setCurrentStreak(0);
            }
        }

        // Segunda pasada: calcular lifeDelta por jugador con bonus de racha.
        List<PlayerRoundOutcome> outcomes = new ArrayList<>();
        for (DuelPlayerRuntime rt : state.getPlayers().values()) {
            Boolean correct = correctness.get(rt.getUserId());
            RawAnswer raw = round.getAnswers().get(rt.getUserId());
            int lifeDelta = 0;
            if (correct == null) {
                // No respondio dentro del deadline
                lifeDelta = -1;
            } else if (!correct) {
                lifeDelta = -1;
                // Bonus de racha: si el rival acerto con streak >= 1 PREVIA, doble penalizacion.
                if (rivalHadStreakBonus(state, rt.getUserId(), streakBefore, correctness)) {
                    lifeDelta -= 1;
                }
            }
            outcomes.add(new PlayerRoundOutcome(
                    rt.getUserId(),
                    correct != null,
                    correct,
                    null,
                    null,
                    raw == null || raw.optionId() == null ? null : UUID.fromString(raw.optionId()),
                    lifeDelta));
        }

        return new RoundResolution(outcomes, new RoundResultPayload.Reveal(correctOptionId, null));
    }

    private boolean rivalHadStreakBonus(DuelMatchState state,
                                        UUID self,
                                        Map<UUID, Integer> streakBefore,
                                        Map<UUID, Boolean> correctness) {
        for (UUID other : state.getPlayers().keySet()) {
            if (other.equals(self)) continue;
            Boolean otherCorrect = correctness.get(other);
            int otherStreakBefore = streakBefore.getOrDefault(other, 0);
            if (Boolean.TRUE.equals(otherCorrect) && otherStreakBefore >= 1) return true;
        }
        return false;
    }

    private UUID correctOptionId(Question question) {
        Optional<QuestionOption> correct = question.getOptions().stream()
                .filter(o -> Boolean.TRUE.equals(o.getIsCorrect()))
                .findFirst();
        return correct.map(QuestionOption::getId).orElse(null);
    }
}
