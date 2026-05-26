package com.versus.api.cards.domain;

import com.versus.api.cards.CardStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cards", indexes = {
        @Index(name = "idx_cards_cat_subcat_status", columnList = "categoria, subcategoria, status"),
        @Index(name = "idx_cards_status_eligible", columnList = "status, eligible_for_survival")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Card {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "categoria", length = 64, nullable = false)
    private String categoria;

    @Column(name = "subcategoria", length = 128, nullable = false)
    private String subcategoria;

    @Column(name = "nombre", columnDefinition = "TEXT", nullable = false)
    private String nombre;

    @Column(name = "valor", precision = 20, scale = 4, nullable = false)
    private BigDecimal valor;

    @Column(name = "unidad", length = 32)
    private String unidad;

    @Column(name = "is_inverse", nullable = false)
    private boolean inverse;

    @Column(name = "eligible_for_survival", nullable = false)
    private boolean eligibleForSurvival;

    @Column(name = "source_url", length = 1024)
    private String sourceUrl;

    @Column(name = "scraped_at")
    private Instant scrapedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 24, nullable = false)
    private CardStatus status;

    @Column(name = "text_hash", length = 64, unique = true, nullable = false)
    private String textHash;
}
