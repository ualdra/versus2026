package com.versus.api.scraping.repo;

import com.versus.api.scraping.domain.SpiderRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpiderRunRepository extends JpaRepository<SpiderRun, UUID> {
}
