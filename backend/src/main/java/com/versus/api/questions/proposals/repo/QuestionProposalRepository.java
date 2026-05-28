package com.versus.api.questions.proposals.repo;

import com.versus.api.questions.QuestionType;
import com.versus.api.questions.proposals.ProposalStatus;
import com.versus.api.questions.proposals.domain.QuestionProposal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface QuestionProposalRepository extends JpaRepository<QuestionProposal, UUID> {

    Page<QuestionProposal> findByStatusOrderByCreatedAtDesc(ProposalStatus status, Pageable pageable);

    Page<QuestionProposal> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<QuestionProposal> findByAuthorIdOrderByCreatedAtDesc(UUID authorId);

    long countByAuthorIdAndStatus(UUID authorId, ProposalStatus status);

    @Query("""
            SELECT COUNT(p) FROM QuestionProposal p
            WHERE p.authorId = :authorId
              AND p.status = :status
              AND p.type = :type
              AND lower(p.text) = lower(:text)
              AND lower(p.proposedAnswer) = lower(:proposedAnswer)
              AND lower(coalesce(p.alternativeAnswer, '')) = lower(:alternativeAnswer)
              AND lower(p.category) = lower(:category)
              AND lower(coalesce(p.sourceUrl, '')) = lower(:sourceUrl)
            """)
    long countExactDuplicate(@Param("authorId") UUID authorId,
                             @Param("status") ProposalStatus status,
                             @Param("type") QuestionType type,
                             @Param("text") String text,
                             @Param("proposedAnswer") String proposedAnswer,
                             @Param("alternativeAnswer") String alternativeAnswer,
                             @Param("category") String category,
                             @Param("sourceUrl") String sourceUrl);
}
