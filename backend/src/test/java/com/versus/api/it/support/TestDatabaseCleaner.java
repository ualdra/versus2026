package com.versus.api.it.support;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Component
@Profile("it")
public class TestDatabaseCleaner {

    private static final List<String> TABLES = List.of(
            "user_achievements",
            "match_invites",
            "friend_requests",
            "friendships",
            "match_answers",
            "match_rounds",
            "match_players",
            "matchmaking_queue",
            "matches",
            "question_reports",
            "question_options",
            "questions",
            "cards",
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

    @Autowired
    private PlatformTransactionManager txManager;

    // Retries on deadlock — the @Scheduled MatchmakingService.pollAndMatch() runs
    // every 1 s and can deadlock with the TRUNCATE if their transactions interleave.
    public void truncateAll() {
        String sql = "TRUNCATE TABLE " + String.join(", ", TABLES) + " RESTART IDENTITY CASCADE";
        TransactionTemplate tt = new TransactionTemplate(txManager);
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                tt.execute(status -> {
                    em.createNativeQuery(sql).executeUpdate();
                    return null;
                });
                return;
            } catch (RuntimeException e) {
                last = e;
                if (attempt < 5) {
                    try { Thread.sleep(150L * attempt); }
                    catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }
        }
        throw last;
    }
}
