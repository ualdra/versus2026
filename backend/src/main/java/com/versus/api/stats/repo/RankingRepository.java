package com.versus.api.stats.repo;

import com.versus.api.stats.domain.Ranking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RankingRepository extends JpaRepository<Ranking, UUID> {
}
