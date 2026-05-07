package com.versus.api.moderation.repo;

import com.versus.api.moderation.ReportStatus;
import com.versus.api.moderation.domain.QuestionReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface QuestionReportRepository extends JpaRepository<QuestionReport, UUID> {

    Page<QuestionReport> findByStatusOrderByCreatedAtDesc(ReportStatus status, Pageable pageable);

    Page<QuestionReport> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByQuestionIdAndStatus(UUID questionId, ReportStatus status);

    boolean existsByQuestionIdAndReportedByAndStatus(UUID questionId, UUID reportedBy, ReportStatus status);
}
