package com.versus.api.cards.repo;

import com.versus.api.cards.domain.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CardRepository extends JpaRepository<Card, UUID> {

    boolean existsByTextHash(String textHash);

    @Query(value = """
            SELECT * FROM cards
            WHERE status = 'ACTIVE'
            ORDER BY random()
            LIMIT 1
            """, nativeQuery = true)
    Optional<Card> findRandomActive();

    @Query(value = """
            SELECT * FROM cards
            WHERE status = 'ACTIVE'
              AND eligible_for_survival = true
              AND subcategoria = :subcategoria
              AND categoria = :categoria
              AND valor != :valor
            ORDER BY random()
            LIMIT 1
            """, nativeQuery = true)
    Optional<Card> findRandomActivePairPartner(String categoria, String subcategoria, java.math.BigDecimal valor);

    @Query(value = """
            SELECT * FROM cards
            WHERE status = 'ACTIVE'
              AND eligible_for_survival = true
            ORDER BY random()
            LIMIT 1
            """, nativeQuery = true)
    Optional<Card> findRandomActiveEligibleForSurvival();

    @Query(value = """
            SELECT * FROM cards
            WHERE status = 'ACTIVE'
              AND eligible_for_survival = false
            ORDER BY random()
            LIMIT 1
            """, nativeQuery = true)
    Optional<Card> findRandomActiveNotEligibleForSurvival();

    @Query(value = """
            SELECT DISTINCT categoria FROM cards WHERE status = 'ACTIVE' ORDER BY categoria
            """, nativeQuery = true)
    List<String> findDistinctCategorias();
}
