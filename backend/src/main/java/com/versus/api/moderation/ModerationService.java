package com.versus.api.moderation;

import com.versus.api.common.exception.ApiException;
import com.versus.api.moderation.domain.QuestionReport;
import com.versus.api.moderation.dto.ReportRequest;
import com.versus.api.moderation.dto.ReportResponse;
import com.versus.api.moderation.dto.ResolveRequest;
import com.versus.api.moderation.repo.QuestionReportRepository;
import com.versus.api.questions.QuestionStatus;
import com.versus.api.questions.domain.Question;
import com.versus.api.questions.repo.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ModerationService {

    static final int REPORT_FLAG_THRESHOLD = 5;

    private final QuestionReportRepository reports;
    private final QuestionRepository questions;

    // ── Report ────────────────────────────────────────────────────────────────

    @Transactional
    public ReportResponse report(UUID questionId, UUID userId, ReportRequest req) {
        Question question = questions.findById(questionId)
                .orElseThrow(() -> ApiException.notFound("Question not found"));

        if (question.getStatus() == QuestionStatus.INACTIVE) {
            throw ApiException.notFound("Question not found");
        }

        if (reports.existsByQuestionIdAndReportedByAndStatus(questionId, userId, ReportStatus.PENDING)) {
            throw ApiException.conflict("You have already reported this question");
        }

        QuestionReport report = QuestionReport.builder()
                .questionId(questionId)
                .reportedBy(userId)
                .reason(req.reason())
                .comment(req.comment())
                .status(ReportStatus.PENDING)
                .build();
        reports.save(report);

        long pendingCount = reports.countByQuestionIdAndStatus(questionId, ReportStatus.PENDING);
        if (pendingCount >= REPORT_FLAG_THRESHOLD && question.getStatus() == QuestionStatus.ACTIVE) {
            question.setStatus(QuestionStatus.FLAGGED);
            questions.save(question);
        }

        return toResponse(report, question);
    }

    // ── List ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ReportResponse> listReports(ReportStatus status, Pageable pageable) {
        Page<QuestionReport> page = status != null
                ? reports.findByStatusOrderByCreatedAtDesc(status, pageable)
                : reports.findAllByOrderByCreatedAtDesc(pageable);

        return page.map(r -> {
            Question q = questions.findById(r.getQuestionId()).orElse(null);
            return toResponse(r, q);
        });
    }

    // ── Resolve ───────────────────────────────────────────────────────────────

    @Transactional
    public ReportResponse resolve(UUID reportId, UUID moderatorId, ResolveRequest req) {
        QuestionReport report = reports.findById(reportId)
                .orElseThrow(() -> ApiException.notFound("Report not found"));

        if (report.getStatus() != ReportStatus.PENDING) {
            throw ApiException.conflict("Report is already resolved");
        }

        Question question = questions.findById(report.getQuestionId()).orElse(null);

        report.setStatus(req.action() == ResolveAction.DISMISS ? ReportStatus.DISMISSED : ReportStatus.RESOLVED);
        report.setResolvedBy(moderatorId);
        report.setResolvedAt(Instant.now());
        report.setAction(req.action());
        reports.save(report);

        if (req.action() == ResolveAction.DELETE_QUESTION && question != null) {
            question.setStatus(QuestionStatus.INACTIVE);
            questions.save(question);
        }

        return toResponse(report, question);
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private ReportResponse toResponse(QuestionReport report, Question question) {
        return new ReportResponse(
                report.getId(),
                report.getQuestionId(),
                question != null ? question.getText() : null,
                question != null ? question.getType().name() : null,
                question != null ? question.getCategory() : null,
                report.getReason(),
                report.getComment(),
                report.getStatus(),
                report.getCreatedAt(),
                report.getResolvedBy(),
                report.getResolvedAt(),
                report.getAction()
        );
    }
}
