package com.versus.api.scraping.repo;

import com.versus.api.scraping.domain.Spider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpiderRepository extends JpaRepository<Spider, UUID> {

    Optional<Spider> findByName(String name);
}
