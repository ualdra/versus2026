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
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Engine de Precision Duel (#92): preguntas numericas con dano diferencial.
 *
 * Reglas:
 * - Cada jugador estima un valor numerico.
 * - deviation% = |value - correctValue| / correctValue * 100.
 * - Quien tenga MENOR desviacion gana el round (no pierde vida).
 * - El de mayor desviacion recibe lifeDelta = -ceil(abs(devA - devB) * FACTOR).
 * - Si no respondio antes del deadline → -2 vidas (mas duro que un fallo, pero sin
 *   ser K.O. al primer timeout con INITIAL_LIVES=3). Su deviation cuenta como 100%
 *   para el promedio final.
 * - Acumulamos deviationSum/deviationCount en el runtime para que el orchestrator
 *   pueda computar avgDeviation en finalize → statsService.recordFinishedGame().
 */
@Component
public class PrecisionDuelEngine implements DuelEngine {

    private static final BigDecimal FACTOR = new BigDecimal("0.02");
    private static final int TIMEOUT_PENALTY = -2;
    private static final double TIMEOUT_DEVIATION = 100.0;

    @Override
    public GameMode mode() {
        return GameMode.PRECISION_DUEL;
    }

    @Override
    public QuestionType questionType() {
        return QuestionType.NUMERIC;
    }

    @Override
    public RoundResolution resolveRound(DuelMatchState state, DuelRoundState round, Question question) {
        BigDecimal correctValue = question.getCorrectValue();
        // Primera pasada: calcular deviation de cada jugador.
        Map<UUID, BigDecimal> deviationByUser = new HashMap<>();
        Map<UUID, Boolean> answeredByUser = new HashMap<>();
        Map<UUID, BigDecimal> valueByUser = new HashMap<>();
        for (DuelPlayerRuntime rt : state.getPlayers().values()) {
            RawAnswer raw = round.getAnswers().get(rt.getUserId());
            if (raw == null || raw.value() == null) {
                answeredByUser.put(rt.getUserId(), false);
                continue;
            }
            answeredByUser.put(rt.getUserId(), true);
            valueByUser.put(rt.getUserId(), raw.value());
            BigDecimal deviation = DeviationStats.deviationPercent(raw.value(), correctValue);
            deviationByUser.put(rt.getUserId(), deviation);
        }

        // Determinar el ganador (menor desviacion entre los que respondieron).
        // En caso de empate exacto, ambos quedan empatados → daño 0.
        UUID winner = null;
        BigDecimal minDeviation = null;
        boolean tie = false;
        for (Map.Entry<UUID, BigDecimal> entry : deviationByUser.entrySet()) {
            BigDecimal dev = entry.getValue();
            if (minDeviation == null || dev.compareTo(minDeviation) < 0) {
                minDeviation = dev;
                winner = entry.getKey();
                tie = false;
            } else if (dev.compareTo(minDeviation) == 0) {
                tie = true;
            }
        }
        if (tie) winner = null;

        // Calcular outcomes por jugador.
        List<PlayerRoundOutcome> outcomes = new ArrayList<>();
        for (DuelPlayerRuntime rt : state.getPlayers().values()) {
            boolean answered = answeredByUser.getOrDefault(rt.getUserId(), false);
            BigDecimal deviation = deviationByUser.get(rt.getUserId());
            BigDecimal value = valueByUser.get(rt.getUserId());

            int lifeDelta;
            Boolean isCorrect;
            double deviationForStats;

            if (!answered) {
                lifeDelta = TIMEOUT_PENALTY;
                isCorrect = false;
                deviationForStats = TIMEOUT_DEVIATION;
                rt.setCurrentStreak(0);
            } else if (winner == null) {
                // empate de desviaciones
                lifeDelta = 0;
                isCorrect = true;
                deviationForStats = DeviationStats.roundToTwo(deviation);
                rt.setCurrentStreak(rt.getCurrentStreak() + 1);
                rt.setBestStreakInMatch(Math.max(rt.getBestStreakInMatch(), rt.getCurrentStreak()));
            } else if (rt.getUserId().equals(winner)) {
                lifeDelta = 0;
                isCorrect = true;
                deviationForStats = DeviationStats.roundToTwo(deviation);
                rt.setCurrentStreak(rt.getCurrentStreak() + 1);
                rt.setBestStreakInMatch(Math.max(rt.getBestStreakInMatch(), rt.getCurrentStreak()));
                rt.setScore(rt.getScore() + 100);
            } else {
                // perdedor: dano = ceil(abs(my_dev - winner_dev) * FACTOR)
                BigDecimal diff = deviation.subtract(minDeviation).abs();
                int damage = diff.multiply(FACTOR).setScale(0, RoundingMode.CEILING).intValue();
                damage = Math.max(1, damage); // siempre al menos -1 al perder
                lifeDelta = -damage;
                isCorrect = false;
                deviationForStats = DeviationStats.roundToTwo(deviation);
                rt.setCurrentStreak(0);
            }

            // Acumular para avgDeviation:
            rt.setDeviationSum(rt.getDeviationSum() + deviationForStats);
            rt.setDeviationCount(rt.getDeviationCount() + 1);

            outcomes.add(new PlayerRoundOutcome(
                    rt.getUserId(),
                    answered,
                    isCorrect,
                    deviation == null ? null : DeviationStats.roundToTwo(deviation),
                    value,
                    null,
                    lifeDelta));
        }

        return new RoundResolution(outcomes, new RoundResultPayload.Reveal(null, correctValue));
    }
}
