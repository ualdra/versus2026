package com.versus.api.questions.repo;

import com.versus.api.questions.domain.QuestionOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface QuestionOptionRepository extends JpaRepository<QuestionOption, UUID> {
}
