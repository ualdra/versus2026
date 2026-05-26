package com.versus.api.practice;

import com.versus.api.common.exception.ApiException;
import com.versus.api.practice.dto.PracticeAnswerRequest;
import com.versus.api.practice.dto.PracticeAnswerResponse;
import com.versus.api.questions.QuestionService;
import com.versus.api.questions.QuestionType;
import com.versus.api.questions.domain.Question;
import com.versus.api.questions.domain.QuestionOption;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PracticeService {

    private static final MathContext PRECISION_CONTEXT = new MathContext(12, RoundingMode.HALF_UP);

    private final QuestionService questionService;

    @Transactional(readOnly = true)
    public PracticeAnswerResponse evaluate(PracticeAnswerRequest request) {
        Question question = questionService.findActiveQuestion(request.questionId());

        if (question.getType() == QuestionType.BINARY) {
            return evaluateBinary(question, request.optionId());
        }
        if (question.getType() == QuestionType.NUMERIC) {
            return evaluateNumeric(question, request.value());
        }
        throw ApiException.validation("Unsupported question type");
    }

    private PracticeAnswerResponse evaluateBinary(Question question, UUID optionId) {
        if (optionId == null) {
            throw ApiException.validation("optionId is required for BINARY questions");
        }

        QuestionOption selected = question.getOptions().stream()
                .filter(o -> o.getId().equals(optionId))
                .findFirst()
                .orElseThrow(() -> ApiException.validation("Option does not belong to question"));

        UUID correctOptionId = question.getOptions().stream()
                .filter(o -> Boolean.TRUE.equals(o.getIsCorrect()))
                .map(QuestionOption::getId)
                .findFirst()
                .orElse(null);

        return new PracticeAnswerResponse(
                Boolean.TRUE.equals(selected.getIsCorrect()),
                correctOptionId,
                null,
                null,
                null,
                question.getExplanation());
    }

    private PracticeAnswerResponse evaluateNumeric(Question question, BigDecimal value) {
        if (value == null) {
            throw ApiException.validation("value is required for NUMERIC questions");
        }

        BigDecimal correctValue = question.getCorrectValue();
        if (correctValue == null || BigDecimal.ZERO.compareTo(correctValue) == 0) {
            throw ApiException.validation("Numeric question has invalid correct value");
        }

        BigDecimal tolerance = question.getTolerancePercent() != null
                ? question.getTolerancePercent()
                : new BigDecimal("5");

        BigDecimal deviationPercent = value
                .subtract(correctValue)
                .abs()
                .divide(correctValue.abs(), PRECISION_CONTEXT)
                .multiply(new BigDecimal("100"));

        boolean correct = deviationPercent.compareTo(tolerance) <= 0;
        double roundedDeviation = deviationPercent.setScale(2, RoundingMode.HALF_UP).doubleValue();

        return new PracticeAnswerResponse(
                correct,
                null,
                correctValue,
                roundedDeviation,
                question.getUnit(),
                question.getExplanation());
    }
}
