package com.versus.api.practice;

import com.versus.api.common.exception.ApiException;
import com.versus.api.common.exception.ErrorCode;
import com.versus.api.practice.dto.PracticeAnswerRequest;
import com.versus.api.practice.dto.PracticeAnswerResponse;
import com.versus.api.questions.QuestionService;
import com.versus.api.questions.QuestionStatus;
import com.versus.api.questions.QuestionType;
import com.versus.api.questions.domain.Question;
import com.versus.api.questions.domain.QuestionOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@DisplayName("PracticeService")
@ExtendWith(MockitoExtension.class)
class PracticeServiceTest {

    @Mock QuestionService questionService;
    @InjectMocks PracticeService practiceService;

    private static final UUID QUESTION_ID  = UUID.fromString("cccc0000-0000-0000-0000-000000000001");
    private static final UUID CORRECT_OPT  = UUID.fromString("dddd0000-0000-0000-0000-000000000002");
    private static final UUID WRONG_OPT    = UUID.fromString("eeee0000-0000-0000-0000-000000000003");

    private Question binaryQuestion() {
        QuestionOption correct = QuestionOption.builder().id(CORRECT_OPT).text("Yes").isCorrect(true).build();
        QuestionOption wrong   = QuestionOption.builder().id(WRONG_OPT).text("No").isCorrect(false).build();
        return Question.builder()
                .id(QUESTION_ID).type(QuestionType.BINARY).status(QuestionStatus.ACTIVE)
                .text("Test?").options(List.of(correct, wrong))
                .explanation("Because yes.").build();
    }

    private Question numericQuestion(BigDecimal correctValue, BigDecimal tolerance) {
        return Question.builder()
                .id(QUESTION_ID).type(QuestionType.NUMERIC).status(QuestionStatus.ACTIVE)
                .text("How many?").correctValue(correctValue).tolerancePercent(tolerance)
                .unit("km").explanation("It is 100 km.").build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // BINARY
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("BINARY")
    @Nested
    class BinaryEvaluation {

        @DisplayName("Opción correcta → correct=true, correctOptionId devuelto")
        @Test
        void opcionCorrecta_correctTrue() {
            when(questionService.findActiveQuestion(QUESTION_ID)).thenReturn(binaryQuestion());
            PracticeAnswerResponse res = practiceService.evaluate(
                    new PracticeAnswerRequest(QUESTION_ID, CORRECT_OPT, null));
            assertThat(res.correct()).isTrue();
            assertThat(res.correctOptionId()).isEqualTo(CORRECT_OPT);
        }

        @DisplayName("Opción incorrecta → correct=false, correctOptionId apunta a la correcta real")
        @Test
        void opcionIncorrecta_correctFalse_correctOptionIdEsLaReal() {
            when(questionService.findActiveQuestion(QUESTION_ID)).thenReturn(binaryQuestion());
            PracticeAnswerResponse res = practiceService.evaluate(
                    new PracticeAnswerRequest(QUESTION_ID, WRONG_OPT, null));
            assertThat(res.correct()).isFalse();
            assertThat(res.correctOptionId()).isEqualTo(CORRECT_OPT);
        }

        @DisplayName("La explicación se devuelve cuando existe")
        @Test
        void explanation_devueltaCuandoExiste() {
            when(questionService.findActiveQuestion(QUESTION_ID)).thenReturn(binaryQuestion());
            PracticeAnswerResponse res = practiceService.evaluate(
                    new PracticeAnswerRequest(QUESTION_ID, CORRECT_OPT, null));
            assertThat(res.explanation()).isEqualTo("Because yes.");
        }

        @DisplayName("optionId nulo → VALIDATION_ERROR")
        @Test
        void optionIdNulo_lanzaValidation() {
            when(questionService.findActiveQuestion(QUESTION_ID)).thenReturn(binaryQuestion());
            assertThatThrownBy(() -> practiceService.evaluate(
                    new PracticeAnswerRequest(QUESTION_ID, null, null)))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        }

        @DisplayName("optionId que no pertenece a la pregunta → VALIDATION_ERROR")
        @Test
        void optionIdAjena_lanzaValidation() {
            when(questionService.findActiveQuestion(QUESTION_ID)).thenReturn(binaryQuestion());
            assertThatThrownBy(() -> practiceService.evaluate(
                    new PracticeAnswerRequest(QUESTION_ID, UUID.randomUUID(), null)))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        }

        @DisplayName("No se devuelven campos numéricos (correctValue, deviation)")
        @Test
        void sinCamposNumericos() {
            when(questionService.findActiveQuestion(QUESTION_ID)).thenReturn(binaryQuestion());
            PracticeAnswerResponse res = practiceService.evaluate(
                    new PracticeAnswerRequest(QUESTION_ID, CORRECT_OPT, null));
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
            when(questionService.findActiveQuestion(QUESTION_ID))
                    .thenReturn(numericQuestion(new BigDecimal("100"), new BigDecimal("5")));
            PracticeAnswerResponse res = practiceService.evaluate(
                    new PracticeAnswerRequest(QUESTION_ID, null, new BigDecimal("102")));
            assertThat(res.correct()).isTrue();
            assertThat(res.correctValue()).isEqualByComparingTo(new BigDecimal("100"));
        }

        @DisplayName("Valor fuera de tolerancia (20%) → correct=false con deviationPercent")
        @Test
        void fueraTolerancia_correctFalse_conDeviation() {
            when(questionService.findActiveQuestion(QUESTION_ID))
                    .thenReturn(numericQuestion(new BigDecimal("100"), new BigDecimal("5")));
            PracticeAnswerResponse res = practiceService.evaluate(
                    new PracticeAnswerRequest(QUESTION_ID, null, new BigDecimal("120")));
            assertThat(res.correct()).isFalse();
            assertThat(res.deviationPercent()).isEqualTo(20.0);
        }

        @DisplayName("tolerancePercent=null usa el 5% por defecto")
        @Test
        void toleranciaNula_usaDefault() {
            when(questionService.findActiveQuestion(QUESTION_ID))
                    .thenReturn(numericQuestion(new BigDecimal("100"), null));
            PracticeAnswerResponse res = practiceService.evaluate(
                    new PracticeAnswerRequest(QUESTION_ID, null, new BigDecimal("102")));
            assertThat(res.correct()).isTrue();
        }

        @DisplayName("La unidad se devuelve en la respuesta")
        @Test
        void unidadDevuelta() {
            when(questionService.findActiveQuestion(QUESTION_ID))
                    .thenReturn(numericQuestion(new BigDecimal("100"), new BigDecimal("5")));
            PracticeAnswerResponse res = practiceService.evaluate(
                    new PracticeAnswerRequest(QUESTION_ID, null, new BigDecimal("100")));
            assertThat(res.unit()).isEqualTo("km");
        }

        @DisplayName("value nulo → VALIDATION_ERROR")
        @Test
        void valueNulo_lanzaValidation() {
            when(questionService.findActiveQuestion(QUESTION_ID))
                    .thenReturn(numericQuestion(new BigDecimal("100"), new BigDecimal("5")));
            assertThatThrownBy(() -> practiceService.evaluate(
                    new PracticeAnswerRequest(QUESTION_ID, null, null)))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        }

        @DisplayName("correctValue=null → VALIDATION_ERROR")
        @Test
        void correctValueNulo_lanzaValidation() {
            when(questionService.findActiveQuestion(QUESTION_ID))
                    .thenReturn(numericQuestion(null, new BigDecimal("5")));
            assertThatThrownBy(() -> practiceService.evaluate(
                    new PracticeAnswerRequest(QUESTION_ID, null, new BigDecimal("50"))))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        }

        @DisplayName("correctValue=0 → VALIDATION_ERROR (evita división por cero)")
        @Test
        void correctValueCero_lanzaValidation() {
            when(questionService.findActiveQuestion(QUESTION_ID))
                    .thenReturn(numericQuestion(BigDecimal.ZERO, new BigDecimal("5")));
            assertThatThrownBy(() -> practiceService.evaluate(
                    new PracticeAnswerRequest(QUESTION_ID, null, new BigDecimal("50"))))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        }

        @DisplayName("No se devuelven campos binarios (correctOptionId)")
        @Test
        void sinCamposBinarios() {
            when(questionService.findActiveQuestion(QUESTION_ID))
                    .thenReturn(numericQuestion(new BigDecimal("100"), new BigDecimal("5")));
            PracticeAnswerResponse res = practiceService.evaluate(
                    new PracticeAnswerRequest(QUESTION_ID, null, new BigDecimal("100")));
            assertThat(res.correctOptionId()).isNull();
        }
    }
}
