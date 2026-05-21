package com.versus.api.it.support;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Profile("it")
public class TestDatabaseCleaner {

    private static final List<String> TABLES = List.of(
            "user_achievements",
            "match_answers",
            "match_rounds",
            "match_players",
            "matchmaking_queue",
            "matches",
            "question_reports",
            "question_options",
            "questions",
            "spider_runs",
            "spiders",
            "rankings",
            "player_stats",
            "media_assets",
            "refresh_tokens",
            "users"
    );

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public void truncateAll() {
        String sql = "TRUNCATE TABLE " + String.join(", ", TABLES) + " RESTART IDENTITY CASCADE";
        em.createNativeQuery(sql).executeUpdate();
    }
}
