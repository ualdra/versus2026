package com.versus.api.questions;

import com.versus.api.cards.domain.Card;
import com.versus.api.questions.dto.QuestionBinaryResponse;
import com.versus.api.questions.dto.QuestionNumericResponse;
import com.versus.api.questions.dto.QuestionOptionResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class CardQuestionFactory {

    /**
     * @param roundToken ID del round (anti-replay en modos de juego). Para practice, pasa cardA.getId().
     */
    public QuestionBinaryResponse buildBinary(UUID roundToken, Card a, Card b) {
        String text = a.isInverse()
                ? "¿Cuál tiene mejor posición en el ranking?"
                : "¿Cuál tiene mayor valor?";
        List<QuestionOptionResponse> options = List.of(
                new QuestionOptionResponse(a.getId(), a.getNombre(), null, a.getUnidad()),
                new QuestionOptionResponse(b.getId(), b.getNombre(), null, b.getUnidad())
        );
        return new QuestionBinaryResponse(
                roundToken, QuestionType.BINARY, text,
                a.getCategoria(), a.getSubcategoria(), a.isInverse(),
                options, a.getScrapedAt());
    }

    public QuestionNumericResponse buildNumeric(UUID roundToken, Card card) {
        String text = "¿Cuál es el valor de " + card.getNombre() + "?";
        return new QuestionNumericResponse(
                roundToken, QuestionType.NUMERIC, text,
                card.getCategoria(), card.getSubcategoria(),
                card.getUnidad(), card.getScrapedAt());
    }
}
