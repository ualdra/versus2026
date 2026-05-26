package com.versus.api.stats;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EloServiceTest {

    private final EloService service = new EloService();

    @Test
    void equalRatingsGiveSymmetricDelta() {
        var result = service.calculate(1000, 1000);

        assertThat(result.winnerDelta()).isEqualTo(16);
        assertThat(result.loserDelta()).isEqualTo(-16);
    }

    @Test
    void underdogWinGetsHigherDelta() {
        var result = service.calculate(900, 1200);

        assertThat(result.winnerDelta()).isGreaterThan(16);
        assertThat(result.loserDelta()).isLessThan(-16);
    }
}
