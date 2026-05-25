package com.versus.api.duel.engine;

import com.versus.api.duel.state.DuelMatchState;
import com.versus.api.duel.state.DuelRoundState;
import com.versus.api.match.GameMode;
import com.versus.api.questions.QuestionType;
import com.versus.api.questions.domain.Question;

/**
 * Strategy registrada por modo de duelo. El {@link com.versus.api.duel.DuelOrchestrator}
 * elige la implementación según {@link GameMode}.
 */
public interface DuelEngine {

    GameMode mode();

    /**
     * Tipo de pregunta que el engine espera del catálogo (BINARY o NUMERIC).
     */
    QuestionType questionType();

    /**
     * Resuelve un round: dadas las respuestas acumuladas en {@code round.answers}
     * (puede haber 0, 1 o 2 jugadores que respondieron antes del deadline), calcula
     * outcomes y mutate del runtime de los jugadores (vidas, racha, score).
     * El engine NO persiste y NO broadcastea — eso lo hace el orchestrator.
     */
    RoundResolution resolveRound(DuelMatchState state, DuelRoundState round, Question question);
}
