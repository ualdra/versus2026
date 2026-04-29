package com.versus.api.scraping.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "spider_runs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpiderRun {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "spider_id", nullable = false)
    private UUID spiderId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "questions_inserted")
    private Integer questionsInserted;

    @Column
    private Integer errors;
}
