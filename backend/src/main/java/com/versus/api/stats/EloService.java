package com.versus.api.stats;

import com.versus.api.stats.dto.EloCalculationResponse;
import org.springframework.stereotype.Service;

@Service
public class EloService {

    private static final int K_FACTOR = 32;

    public EloCalculationResponse calculate(int winnerRating, int loserRating) {
        double winnerExpected = expectedScore(winnerRating, loserRating);
        double loserExpected = expectedScore(loserRating, winnerRating);

        int winnerDelta = (int) Math.round(K_FACTOR * (1.0 - winnerExpected));
        int loserDelta = (int) Math.round(K_FACTOR * (0.0 - loserExpected));

        return new EloCalculationResponse(winnerDelta, loserDelta);
    }

    private double expectedScore(int ownRating, int opponentRating) {
        return 1.0 / (1.0 + Math.pow(10.0, (opponentRating - ownRating) / 400.0));
    }
}
