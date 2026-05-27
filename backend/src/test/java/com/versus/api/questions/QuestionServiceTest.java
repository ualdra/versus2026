package com.versus.api.questions;

import com.versus.api.cards.CardService;
import com.versus.api.cards.domain.Card;
import com.versus.api.questions.dto.QuestionBinaryResponse;
import com.versus.api.questions.dto.QuestionNumericResponse;
import com.versus.api.questions.dto.QuestionResponse;
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
import static org.mockito.Mockito.*;

@DisplayName("QuestionService")
@ExtendWith(MockitoExtension.class)
class QuestionServiceTest {

    @Mock CardService cardService;
    @Mock CardQuestionFactory cardFactory;
    @InjectMocks QuestionService questionService;

    private Card card(UUID id, boolean eligibleForSurvival) {
        return Card.builder()
                .id(id)
                .nombre("Test")
                .categoria("geo")
                .subcategoria("europa")
                .valor(new BigDecimal("100"))
                .unidad("km")
                .eligibleForSurvival(eligibleForSurvival)
                .inverse(false)
                .build();
    }

    @DisplayName("getRandom")
    @Nested
    class GetRandom {

        @DisplayName("tipo BINARY delega a getRandomPairForSurvival y buildBinary")
        @Test
        void binarioDelegaAlPar() {
            UUID idA = UUID.randomUUID();
            UUID idB = UUID.randomUUID();
            Card a = card(idA, true);
            Card b = card(idB, true);
            CardService.CardPair pair = new CardService.CardPair(a, b);
            when(cardService.getRandomPairForSurvival()).thenReturn(pair);
            QuestionBinaryResponse expected = mock(QuestionBinaryResponse.class);
            when(cardFactory.buildBinary(idA, a, b)).thenReturn(expected);

            QuestionResponse res = questionService.getRandom(QuestionType.BINARY, null);

            assertThat(res).isSameAs(expected);
            verify(cardService).getRandomPairForSurvival();
            verify(cardFactory).buildBinary(idA, a, b);
        }

        @DisplayName("tipo NUMERIC delega a getRandomCard y buildNumeric")
        @Test
        void numericoDelegaAGetRandomCard() {
            UUID id = UUID.randomUUID();
            Card c = card(id, false);
            when(cardService.getRandomCard()).thenReturn(c);
            QuestionNumericResponse expected = mock(QuestionNumericResponse.class);
            when(cardFactory.buildNumeric(id, c)).thenReturn(expected);

            QuestionResponse res = questionService.getRandom(QuestionType.NUMERIC, null);

            assertThat(res).isSameAs(expected);
            verify(cardService).getRandomCard();
            verify(cardFactory).buildNumeric(id, c);
        }

        @DisplayName("tipo null trata como BINARY (par)")
        @Test
        void tipoNullTrataComoBinario() {
            UUID idA = UUID.randomUUID();
            Card a = card(idA, true);
            Card b = card(UUID.randomUUID(), true);
            CardService.CardPair pair = new CardService.CardPair(a, b);
            when(cardService.getRandomPairForSurvival()).thenReturn(pair);
            when(cardFactory.buildBinary(idA, a, b)).thenReturn(mock(QuestionBinaryResponse.class));

            questionService.getRandom(null, null);

            verify(cardService).getRandomPairForSurvival();
        }
    }

    @DisplayName("getCategories")
    @Nested
    class GetCategories {

        @DisplayName("Delega a CardService")
        @Test
        void delegaACardService() {
            when(cardService.getCategories()).thenReturn(List.of("sport", "geo"));
            List<String> cats = questionService.getCategories();
            assertThat(cats).containsExactly("sport", "geo");
            verify(cardService).getCategories();
        }
    }
}
