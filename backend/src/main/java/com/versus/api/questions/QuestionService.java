package com.versus.api.questions;

import com.versus.api.cards.CardService;
import com.versus.api.cards.domain.Card;
import com.versus.api.questions.dto.QuestionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Endpoint público GET /api/questions/* (usado por Practice).
 * El id devuelto en QuestionResponse es el cardId, para que PracticeService
 * pueda buscar la card al evaluar la respuesta.
 */
@Service
@RequiredArgsConstructor
public class QuestionService {

    private final CardService cards;
    private final CardQuestionFactory cardFactory;

    @Transactional(readOnly = true)
    public QuestionResponse getRandom(QuestionType type, String category) {
        if (type == QuestionType.NUMERIC) {
            Card card = cards.getRandomCard();
            return cardFactory.buildNumeric(card.getId(), card);
        }
        CardService.CardPair pair = cards.getRandomPairForSurvival();
        return cardFactory.buildBinary(pair.a().getId(), pair.a(), pair.b());
    }

    @Transactional(readOnly = true)
    public List<String> getCategories() {
        return cards.getCategories();
    }
}
