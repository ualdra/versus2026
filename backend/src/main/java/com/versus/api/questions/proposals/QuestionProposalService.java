package com.versus.api.questions.proposals;

import com.versus.api.common.exception.ApiException;
import com.versus.api.questions.QuestionStatus;
import com.versus.api.questions.QuestionType;
import com.versus.api.questions.domain.Question;
import com.versus.api.questions.domain.QuestionOption;
import com.versus.api.questions.proposals.domain.QuestionProposal;
import com.versus.api.questions.proposals.dto.ProposeQuestionRequest;
import com.versus.api.questions.proposals.dto.QuestionProposalHistoryResponse;
import com.versus.api.questions.proposals.dto.QuestionProposalResponse;
import com.versus.api.questions.proposals.dto.RejectProposalRequest;
import com.versus.api.questions.proposals.repo.QuestionProposalRepository;
import com.versus.api.questions.repo.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuestionProposalService {

    public static final int PENDING_LIMIT = 5;

    private final QuestionProposalRepository proposals;
    private final QuestionRepository questions;

    @Transactional
    public QuestionProposalResponse propose(UUID authorId, ProposeQuestionRequest request) {
        if (authorId == null) {
            throw ApiException.unauthorized("Authentication required");
        }
        if (request.type() == null) {
            throw ApiException.validation("Question type is required");
        }

        String text = normalizeRequired(request.text(), "Question text is required");
        String category = normalizeRequired(request.category(), "Category is required");
        String sourceUrl = normalizeOptional(request.sourceUrl());
        String proposedAnswer = normalizeAnswer(request.type(), request.proposedAnswer());
        String alternativeAnswer = normalizeAlternativeAnswer(request.type(), request.alternativeAnswer(), proposedAnswer);

        if (proposals.countExactDuplicate(
                authorId,
                ProposalStatus.REJECTED,
                request.type(),
                text,
                proposedAnswer,
                alternativeAnswer == null ? "" : alternativeAnswer,
                category,
                sourceUrl == null ? "" : sourceUrl) > 0) {
            throw ApiException.conflict("This exact proposal was already rejected and cannot be submitted again");
        }

        long pending = proposals.countByAuthorIdAndStatus(authorId, ProposalStatus.PENDING);
        if (pending >= PENDING_LIMIT) {
            throw ApiException.validation("You already have 5 pending question proposals. Wait for moderation before sending more.");
        }

        QuestionProposal proposal = QuestionProposal.builder()
                .authorId(authorId)
                .type(request.type())
                .text(text)
                .proposedAnswer(proposedAnswer)
                .alternativeAnswer(alternativeAnswer)
                .category(category)
                .sourceUrl(sourceUrl)
                .status(ProposalStatus.PENDING)
                .build();

        return toResponse(proposals.save(proposal));
    }

    @Transactional(readOnly = true)
    public QuestionProposalHistoryResponse listMine(UUID authorId) {
        int pendingCount = (int) proposals.countByAuthorIdAndStatus(authorId, ProposalStatus.PENDING);
        return new QuestionProposalHistoryResponse(
                pendingCount,
                PENDING_LIMIT,
                proposals.findByAuthorIdOrderByCreatedAtDesc(authorId).stream()
                        .map(this::toResponse)
                        .toList());
    }

    @Transactional(readOnly = true)
    public Page<QuestionProposalResponse> listForModeration(ProposalStatus status, Pageable pageable) {
        Page<QuestionProposal> page = status != null
                ? proposals.findByStatusOrderByCreatedAtDesc(status, pageable)
                : proposals.findAllByOrderByCreatedAtDesc(pageable);
        return page.map(this::toResponse);
    }

    @Transactional
    public QuestionProposalResponse approve(UUID proposalId, UUID moderatorId) {
        QuestionProposal proposal = findPending(proposalId);
        Question activeQuestion = buildActiveQuestion(proposal);
        questions.save(activeQuestion);

        proposal.setStatus(ProposalStatus.APPROVED);
        proposal.setReviewedBy(moderatorId);
        proposal.setReviewedAt(Instant.now());
        proposal.setRejectReason(null);
        return toResponse(proposals.save(proposal));
    }

    @Transactional
    public QuestionProposalResponse reject(UUID proposalId, UUID moderatorId, RejectProposalRequest request) {
        QuestionProposal proposal = findPending(proposalId);
        proposal.setStatus(ProposalStatus.REJECTED);
        proposal.setReviewedBy(moderatorId);
        proposal.setReviewedAt(Instant.now());
        proposal.setRejectReason(normalizeRequired(request.rejectReason(), "Reject reason is required"));
        return toResponse(proposals.save(proposal));
    }

    private QuestionProposal findPending(UUID proposalId) {
        QuestionProposal proposal = proposals.findById(proposalId)
                .orElseThrow(() -> ApiException.notFound("Question proposal not found"));
        if (proposal.getStatus() != ProposalStatus.PENDING) {
            throw ApiException.conflict("Question proposal has already been reviewed");
        }
        return proposal;
    }

    private Question buildActiveQuestion(QuestionProposal proposal) {
        Question question = Question.builder()
                .text(proposal.getText())
                .type(proposal.getType())
                .category(proposal.getCategory())
                .sourceUrl(proposal.getSourceUrl())
                .scrapedAt(Instant.now())
                .status(QuestionStatus.ACTIVE)
                .textHash(sha256("proposal|" + proposal.getId() + "|" + proposal.getType() + "|" + proposal.getText()))
                .explanation("Community proposal approved by moderation")
                .build();

        if (proposal.getType() == QuestionType.NUMERIC) {
            question.setCorrectValue(new BigDecimal(proposal.getProposedAnswer()));
            return question;
        }

        question.getOptions().add(QuestionOption.builder()
                .question(question)
                .text(proposal.getProposedAnswer())
                .isCorrect(true)
                .build());
        question.getOptions().add(QuestionOption.builder()
                .question(question)
                .text(proposal.getAlternativeAnswer())
                .isCorrect(false)
                .build());
        return question;
    }

    private QuestionProposalResponse toResponse(QuestionProposal proposal) {
        return new QuestionProposalResponse(
                proposal.getId(),
                proposal.getAuthorId(),
                proposal.getType(),
                proposal.getText(),
                proposal.getProposedAnswer(),
                proposal.getAlternativeAnswer(),
                proposal.getCategory(),
                proposal.getStatus(),
                proposal.getReviewedBy(),
                proposal.getReviewedAt(),
                proposal.getRejectReason(),
                proposal.getSourceUrl(),
                proposal.getCreatedAt());
    }

    private String normalizeAnswer(QuestionType type, String raw) {
        String value = normalizeRequired(raw, "Proposed answer is required");
        if (type == QuestionType.BINARY) {
            return value;
        }
        if (type == QuestionType.NUMERIC) {
            try {
                BigDecimal decimal = new BigDecimal(value);
                BigDecimal stripped = decimal.stripTrailingZeros();
                if (stripped.scale() < 0) {
                    stripped = stripped.setScale(0);
                }
                return stripped.toPlainString();
            } catch (NumberFormatException ex) {
                throw ApiException.validation("Numeric proposals require a valid numeric answer");
            }
        }
        throw ApiException.validation("Unsupported question type");
    }

    private String normalizeAlternativeAnswer(QuestionType type, String raw, String proposedAnswer) {
        if (type == QuestionType.NUMERIC) {
            return null;
        }
        if (type == QuestionType.BINARY) {
            String alternative = normalizeRequired(raw, "Alternative answer is required for binary proposals");
            if (alternative.equalsIgnoreCase(proposedAnswer)) {
                throw ApiException.validation("Binary proposal answers must be different");
            }
            return alternative;
        }
        throw ApiException.validation("Unsupported question type");
    }

    private String normalizeRequired(String raw, String message) {
        String value = normalizeOptional(raw);
        if (value == null) {
            throw ApiException.validation(message);
        }
        return value;
    }

    private String normalizeOptional(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().replaceAll("\\s+", " ");
        return value.isBlank() ? null : value;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes()));
        } catch (Exception e) {
            throw ApiException.validation("Could not hash approved proposal");
        }
    }
}
