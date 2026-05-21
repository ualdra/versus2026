package com.versus.api.it.support;

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
}
