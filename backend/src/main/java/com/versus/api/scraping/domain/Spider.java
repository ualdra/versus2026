package com.versus.api.scraping.domain;

import com.versus.api.scraping.SpiderStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "spiders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Spider {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "target_url", length = 1024)
    private String targetUrl;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SpiderStatus status;

    @Column(name = "managed_by")
    private UUID managedBy;
}
