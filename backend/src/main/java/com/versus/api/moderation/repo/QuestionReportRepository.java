package com.versus.api.moderation.repo;

import com.versus.api.moderation.domain.QuestionReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface QuestionReportRepository extends JpaRepository<QuestionReport, UUID> {
}
