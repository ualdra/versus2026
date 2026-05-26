package com.versus.api.cards;

import com.versus.api.cards.domain.Card;
import com.versus.api.cards.repo.CardRepository;
import com.versus.api.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CardService {

    private static final int PAIR_MAX_RETRIES = 5;

    private final CardRepository cards;

    public record CardPair(Card a, Card b) {}

    @Transactional(readOnly = true)
    public Card getById(java.util.UUID id) {
        return cards.findById(id)
                .orElseThrow(() -> ApiException.notFound("Card not found: " + id));
    }

    @Transactional(readOnly = true)
    public Card getRandomCard() {
        return cards.findRandomActive()
                .orElseThrow(() -> ApiException.notFound("No active card found"));
    }

    @Transactional(readOnly = true)
    public CardPair getRandomPairForSurvival() {
        for (int i = 0; i < PAIR_MAX_RETRIES; i++) {
            Card a = cards.findRandomActiveEligibleForSurvival()
                    .orElseThrow(() -> ApiException.notFound("No active eligible card found"));
            var maybeB = cards.findRandomActivePairPartner(
                    a.getCategoria(), a.getSubcategoria(), a.getValor());
            if (maybeB.isPresent()) {
                return new CardPair(a, maybeB.get());
            }
        }
        throw ApiException.conflict("Could not find a valid card pair after " + PAIR_MAX_RETRIES + " attempts");
    }
}
