package com.versus.api.match.repo;

import com.versus.api.match.domain.MatchAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MatchAnswerRepository extends JpaRepository<MatchAnswer, UUID> {
}
