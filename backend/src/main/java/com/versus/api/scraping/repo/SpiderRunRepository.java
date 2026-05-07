package com.versus.api.scraping.repo;

import com.versus.api.scraping.domain.SpiderRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpiderRunRepository extends JpaRepository<SpiderRun, UUID> {

    List<SpiderRun> findBySpiderIdOrderByStartedAtDesc(UUID spiderId);

    Optional<SpiderRun> findFirstBySpiderIdOrderByStartedAtDesc(UUID spiderId);
}
