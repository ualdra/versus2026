package com.versus.api.it.support;

import com.versus.api.cards.domain.Card;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@Profile("it")
public class TestQueryHelper {

    @PersistenceContext
    private EntityManager em;

    /** Compat: para tests basados aún en preguntas (moderación). */
    @Transactional(readOnly = true)
    public UUID correctOptionFor(UUID questionId) {
        return em.createQuery(
                        "SELECT o.id FROM QuestionOption o " +
                        "WHERE o.question.id = :qid AND o.isCorrect = true",
                        UUID.class)
                .setParameter("qid", questionId)
                .setMaxResults(1)
                .getSingleResult();
    }

    /** Compat: para tests basados aún en preguntas (moderación). */
    @Transactional(readOnly = true)
    public UUID wrongOptionFor(UUID questionId) {
        return em.createQuery(
                        "SELECT o.id FROM QuestionOption o " +
                        "WHERE o.question.id = :qid AND o.isCorrect = false",
                        UUID.class)
                .setParameter("qid", questionId)
                .setMaxResults(1)
                .getSingleResult();
    }

    /**
     * Para survival: lee el par de cartas actual del jugador y devuelve el ID
     * de la carta ganadora. La lógica replica la de GameService
     * (no-inverso → mayor valor gana; inverso → menor valor gana).
     */
    @Transactional(readOnly = true)
    public UUID winningCardIdForSession(UUID sessionId, UUID userId) {
        UUID[] pair = pairFor(sessionId, userId);
        Card a = em.find(Card.class, pair[0]);
        Card b = em.find(Card.class, pair[1]);
        Card winner = a.isInverse()
                ? (a.getValor().compareTo(b.getValor()) <= 0 ? a : b)
                : (a.getValor().compareTo(b.getValor()) >= 0 ? a : b);
        return winner.getId();
    }

    /** Para survival: ID de la carta perdedora del par actual. */
    @Transactional(readOnly = true)
    public UUID losingCardIdForSession(UUID sessionId, UUID userId) {
        UUID winner = winningCardIdForSession(sessionId, userId);
        UUID[] pair = pairFor(sessionId, userId);
        return winner.equals(pair[0]) ? pair[1] : pair[0];
    }

    private UUID[] pairFor(UUID sessionId, UUID userId) {
        Object[] row = (Object[]) em.createNativeQuery(
                        "SELECT current_card_a_id, current_card_b_id FROM match_players " +
                        "WHERE match_id = :mid AND user_id = :uid")
                .setParameter("mid", sessionId)
                .setParameter("uid", userId)
                .getSingleResult();
        return new UUID[]{(UUID) row[0], (UUID) row[1]};
    }

    /** Para single-player IT donde el match tiene un único jugador (owner). */
    @Transactional(readOnly = true)
    public UUID losingCardIdForSession(UUID sessionId) {
        UUID owner = (UUID) em.createNativeQuery(
                        "SELECT owner_user_id FROM matches WHERE id = :mid")
                .setParameter("mid", sessionId)
                .getSingleResult();
        return losingCardIdForSession(sessionId, owner);
    }
}
