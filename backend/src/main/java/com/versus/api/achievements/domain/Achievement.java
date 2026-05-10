package com.versus.api.achievements.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "achievements", indexes = {
        @Index(name = "idx_achievements_key", columnList = "achievement_key", unique = true),
        @Index(name = "idx_achievements_category", columnList = "category")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Achievement {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "achievement_key", nullable = false, unique = true, length = 80)
    private String key;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "icon_key", nullable = false, length = 80)
    private String iconKey;

    @Column(nullable = false, length = 80)
    private String category;
}
