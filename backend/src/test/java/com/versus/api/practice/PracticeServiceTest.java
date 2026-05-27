package com.versus.api.practice;

import com.versus.api.cards.CardService;
import com.versus.api.cards.domain.Card;
import com.versus.api.common.exception.ApiException;
import com.versus.api.common.exception.ErrorCode;
import com.versus.api.practice.dto.PracticeAnswerRequest;
import com.versus.api.practice.dto.PracticeAnswerResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@DisplayName("PracticeService")
@ExtendWith(MockitoExtension.class)
class PracticeServiceTest {

    @Mock CardService cardService;
    @InjectMocks PracticeService practiceService;

    private static final UUID CARD_ID     = UUID.fromString("cccc0000-0000-0000-0000-000000000001");
    private static final UUID CORRECT_OPT = CARD_ID; // para binario, correctOptionId == cardA.getId()
    private static final UUID WRONG_OPT   = UUID.fromString("eeee0000-0000-0000-0000-000000000003");

    private Card numericCard(BigDecimal valor) {
        return Card.builder()
                .id(CARD_ID)
                .nombre("Test")
                .categoria("geo")
                .subcategoria("europa")
                .valor(valor)
                .unidad("km")
                .eligibleForSurvival(false)
                .inverse(false)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // BINARY (evaluateBinary: optionId == cardA.getId() → correcto)
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("BINARY")
    @Nested
    class BinaryEvaluation {

        @DisplayName("optionId == cardA.getId() → correct=true")
        @Test
        void opcionCorrecta_correctTrue() {
            when(cardService.getById(CARD_ID)).thenReturn(numericCard(new BigDecimal("100")));
            PracticeAnswerResponse res = practiceService.evaluate(
                    new PracticeAnswerRequest(CARD_ID, CORRECT_OPT, null));
            assertThat(res.correct()).isTrue();
            assertThat(res.correctOptionId()).isEqualTo(CORRECT_OPT);
        }

        @DisplayName("optionId != cardA.getId() → correct=false")
        @Test
        void opcionIncorrecta_correctFalse() {
            when(cardService.getById(CARD_ID)).thenReturn(numericCard(new BigDecimal("100")));
            PracticeAnswerResponse res = practiceService.evaluate(
                    new PracticeAnswerRequest(CARD_ID, WRONG_OPT, null));
            assertThat(res.correct()).isFalse();
            assertThat(res.correctOptionId()).isEqualTo(CORRECT_OPT);
        }

        @DisplayName("No se devuelven campos numéricos (correctValue, deviation)")
        @Test
        void sinCamposNumericos() {
            when(cardService.getById(CARD_ID)).thenReturn(numericCard(new BigDecimal("100")));
            PracticeAnswerResponse res = practiceService.evaluate(
                    new PracticeAnswerRequest(CARD_ID, CORRECT_OPT, null));
            assertThat(res.correctValue()).isNull();
            assertThat(res.deviationPercent()).isNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // NUMERIC
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("NUMERIC")
    @Nested
    class NumericEvaluation {

        @DisplayName("Valor dentro de tolerancia (2%) → correct=true")
        @Test
        void dentroTolerancia_correctTrue() {
            when(cardService.getById(CARD_ID)).thenReturn(numericCard(new BigDecimal("100")));
            PracticeAnswerResponse res = practiceService.evaluate(
                    new PracticeAnswerRequest(CARD_ID, null, new BigDecimal("102")));
            assertThat(res.correct()).isTrue();
            assertThat(res.correctValue()).isEqualByComparingTo(new BigDecimal("100"));
        }

        @DisplayName("Valor fuera de tolerancia (20%) → correct=false con deviationPercent")
        @Test
        void fueraTolerancia_correctFalse_conDeviation() {
            when(cardService.getById(CARD_ID)).thenReturn(numericCard(new BigDecimal("100")));
            PracticeAnswerResponse res = practiceService.evaluate(
                    new PracticeAnswerRequest(CARD_ID, null, new BigDecimal("120")));
            assertThat(res.correct()).isFalse();
            assertThat(res.deviationPercent()).isEqualTo(20.0);
        }

        @DisplayName("La unidad se devuelve en la respuesta")
        @Test
        void unidadDevuelta() {
            when(cardService.getById(CARD_ID)).thenReturn(numericCard(new BigDecimal("100")));
            PracticeAnswerResponse res = practiceService.evaluate(
                    new PracticeAnswerRequest(CARD_ID, null, new BigDecimal("100")));
            assertThat(res.unit()).isEqualTo("km");
        }

        @DisplayName("ni optionId ni value → VALIDATION_ERROR")
        @Test
        void ambosNulos_lanzaValidation() {
            when(cardService.getById(CARD_ID)).thenReturn(numericCard(new BigDecimal("100")));
            assertThatThrownBy(() -> practiceService.evaluate(
                    new PracticeAnswerRequest(CARD_ID, null, null)))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        }

        @DisplayName("valor=0 en card → VALIDATION_ERROR (evita división por cero)")
        @Test
        void correctValueCero_lanzaValidation() {
            when(cardService.getById(CARD_ID)).thenReturn(numericCard(BigDecimal.ZERO));
            assertThatThrownBy(() -> practiceService.evaluate(
                    new PracticeAnswerRequest(CARD_ID, null, new BigDecimal("50"))))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        }

        @DisplayName("No se devuelven campos binarios (correctOptionId)")
        @Test
        void sinCamposBinarios() {
            when(cardService.getById(CARD_ID)).thenReturn(numericCard(new BigDecimal("100")));
            PracticeAnswerResponse res = practiceService.evaluate(
                    new PracticeAnswerRequest(CARD_ID, null, new BigDecimal("100")));
            assertThat(res.correctOptionId()).isNull();
        }
    }
}
