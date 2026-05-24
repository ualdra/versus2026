package com.versus.api.questions;

import com.versus.api.common.exception.ApiException;
import com.versus.api.questions.domain.Question;
import com.versus.api.questions.domain.QuestionOption;
import com.versus.api.questions.dto.QuestionBinaryResponse;
import com.versus.api.questions.dto.QuestionNumericResponse;
import com.versus.api.questions.dto.QuestionOptionResponse;
import com.versus.api.questions.dto.QuestionResponse;
import com.versus.api.questions.repo.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionRepository questions;

    @Transactional(readOnly = true)
    public QuestionResponse getRandom(QuestionType type, String category) {
        Question question = findRandomActiveQuestion(type, category);
        return toResponse(question);
    }

    @Transactional(readOnly = true)
    public QuestionResponse getById(UUID id) {
        Question question = questions.findById(id)
                .filter(q -> q.getStatus() == QuestionStatus.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("Question not found"));
        return toResponse(question);
    }

    @Transactional(readOnly = true)
    public List<String> getCategories() {
        return questions.findDistinctCategories(QuestionStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public Question findRandomActiveQuestion(QuestionType type, String category) {
        Question question = questions.findRandomActive(type == null ? null : type.name(), normalizeCategory(category))
                .orElseThrow(() -> ApiException.notFound("No active question found"));
        initializeLazyAssociations(question);
        return question;
    }

    @Transactional(readOnly = true)
    public Question findActiveQuestion(UUID id, QuestionType expectedType) {
        Question question = questions.findById(id)
                .filter(q -> q.getStatus() == QuestionStatus.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("Question not found"));
        if (question.getType() != expectedType) {
            throw ApiException.validation("Question type does not match game mode");
        }
        initializeLazyAssociations(question);
        return question;
    }

    /**
     * Fuerza la carga de colecciones lazy mientras la sesión Hibernate sigue abierta.
     * Necesario para consumidores que usan la entidad fuera de un contexto transaccional
     * (p.ej. DuelOrchestrator corre en el scheduler thread con open-in-view=false).
     */
    private void initializeLazyAssociations(Question question) {
        if (question.getType() == QuestionType.BINARY && question.getOptions() != null) {
            question.getOptions().size();
        }
    }

    public QuestionResponse toResponse(Question question) {
        if (question.getType() == QuestionType.BINARY) {
            return new QuestionBinaryResponse(
                    question.getId(),
                    question.getType(),
                    question.getText(),
                    question.getCategory(),
                    question.getOptions().stream()
                            .sorted(Comparator.comparing(QuestionOption::getText))
                            .map(o -> new QuestionOptionResponse(o.getId(), o.getText()))
                            .toList(),
                    question.getScrapedAt());
        }
        if (question.getType() == QuestionType.NUMERIC) {
            return new QuestionNumericResponse(
                    question.getId(),
                    question.getType(),
                    question.getText(),
                    question.getCategory(),
                    question.getUnit(),
                    question.getScrapedAt());
        }
        throw ApiException.validation("Unsupported question type");
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        return category;
    }
}
