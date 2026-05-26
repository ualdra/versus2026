package com.versus.api.duel.engine;

import com.versus.api.duel.dto.PlayerRoundOutcome;
import com.versus.api.duel.dto.RoundResultPayload;
import com.versus.api.duel.state.DuelMatchState;
import com.versus.api.duel.state.DuelPlayerRuntime;
import com.versus.api.duel.state.DuelRoundState;
import com.versus.api.duel.state.PendingEffect;
import com.versus.api.duel.state.RawAnswer;
import com.versus.api.duel.state.SabotageType;
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
 * Engine de Sabotaje (#93): mecanica binaria + capa de efectos.
 *
 * Extiende la logica de BinaryDuelEngine:
 *  - Score y streak igual que binario.
 *  - Cada 3 aciertos consecutivos un jugador gana 1 sabotageToken.
 *  - LIFE_STEAL (efecto aplicado por el atacante en el round anterior):
 *      si el target FALLA este round, el atacante recupera +1 vida (max 3).
 *
 * TIME_BOMB y OBFUSCATION se aplican antes del round (timer reducido,
 * opcion ocultada) — eso lo gestiona el orchestrator en `startNextRound`.
 * Este engine sólo necesita registrar LIFE_STEAL para reaccionar al fallo.
 *
 * NOTA: el orchestrator ya consumio y limpio `incomingEffects` para construir
 * el round (snapshot en `round.effectsApplied`). Aqui leemos
 * `round.effectsApplied` para detectar LIFE_STEAL contra cada jugador y
 * recuperamos el atacante via la lista de eventos sabotaje emitidos
 * (almacenados implicitamente: usamos un mapa `sabotageActivatorByTarget`
 * dentro del state, pasamos la informacion por effectsApplied).
 *
 * Implementacion simplificada para el alcance del issue: si el target tiene
 * un efecto LIFE_STEAL aplicado y falla, el rival (unico otro jugador en 1v1)
 * recupera +1 vida.
 */
@Component
public class SabotageEngine implements DuelEngine {

    private static final int TOKEN_THRESHOLD = 3;
    private static final int MAX_LIVES = 3;

    @Override
    public GameMode mode() {
        return GameMode.SABOTAGE;
    }

    @Override
    public QuestionType questionType() {
        return QuestionType.BINARY;
    }

    @Override
    public RoundResolution resolveRound(DuelMatchState state, DuelRoundState round, Question question) {
        UUID correctOptionId = correctOptionId(question);
        Map<UUID, Integer> streakBefore = new HashMap<>();
        Map<UUID, Boolean> correctness = new HashMap<>();
        state.getPlayers().forEach((uid, rt) -> streakBefore.put(uid, rt.getCurrentStreak()));

        // 1ª pasada: marcar correctness, actualizar score/streak/tokens.
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
                // Ganar token al alcanzar multiplo de TOKEN_THRESHOLD (3, 6, 9…).
                if (rt.getCurrentStreak() % TOKEN_THRESHOLD == 0) {
                    rt.setSabotageTokens(rt.getSabotageTokens() + 1);
                }
            } else {
                rt.setCurrentStreak(0);
            }
        }

        // 2ª pasada: lifeDeltas con bonus binario + LIFE_STEAL si aplica.
        List<PlayerRoundOutcome> outcomes = new ArrayList<>();
        for (DuelPlayerRuntime rt : state.getPlayers().values()) {
            Boolean correct = correctness.get(rt.getUserId());
            RawAnswer raw = round.getAnswers().get(rt.getUserId());
            int lifeDelta = 0;
            if (correct == null) {
                lifeDelta = -1; // timeout
            } else if (!correct) {
                lifeDelta = -1;
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

        // 3ª pasada: aplicar LIFE_STEAL. Si target tenia efecto y fallo, el rival recupera +1 vida.
        applyLifeSteal(state, round, correctness);

        return new RoundResolution(outcomes, new RoundResultPayload.Reveal(correctOptionId, null));
    }

    private void applyLifeSteal(DuelMatchState state,
                                DuelRoundState round,
                                Map<UUID, Boolean> correctness) {
        round.getEffectsApplied().forEach((target, type) -> {
            if (type != SabotageType.LIFE_STEAL) return;
            Boolean targetCorrect = correctness.get(target);
            if (!Boolean.FALSE.equals(targetCorrect)) return; // solo si fallo (no timeout, no acierto)
            // En 1v1 el atacante es el unico otro jugador.
            for (DuelPlayerRuntime other : state.getPlayers().values()) {
                if (other.getUserId().equals(target)) continue;
                int newLives = Math.min(MAX_LIVES, other.getLivesRemaining() + 1);
                other.setLivesRemaining(newLives);
            }
        });
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

    // Helper publico para que los tests puedan ejercitar la asignacion de tokens.
    public static int tokenThreshold() {
        return TOKEN_THRESHOLD;
    }

    @SuppressWarnings("unused")
    private static PendingEffect _ref = new PendingEffect(SabotageType.LIFE_STEAL, null);
}
