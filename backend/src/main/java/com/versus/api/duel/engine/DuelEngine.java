package com.versus.api.duel.engine;

import com.versus.api.duel.state.DuelMatchState;
import com.versus.api.duel.state.DuelRoundState;
import com.versus.api.match.GameMode;
import com.versus.api.questions.QuestionType;

public interface DuelEngine {

    GameMode mode();

    QuestionType questionType();

    RoundResolution resolveRound(DuelMatchState state, DuelRoundState round, CardRoundContext context);
}
