package com.versus.api.questions.proposals;

import com.versus.api.common.exception.ApiException;
import com.versus.api.common.exception.ErrorCode;
import com.versus.api.questions.QuestionStatus;
import com.versus.api.questions.QuestionType;
import com.versus.api.questions.domain.Question;
import com.versus.api.questions.proposals.domain.QuestionProposal;
import com.versus.api.questions.proposals.dto.ProposeQuestionRequest;
import com.versus.api.questions.proposals.dto.RejectProposalRequest;
import com.versus.api.questions.proposals.repo.QuestionProposalRepository;
import com.versus.api.questions.repo.QuestionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("QuestionProposalService")
@ExtendWith(MockitoExtension.class)
class QuestionProposalServiceTest {

    @Mock QuestionProposalRepository proposals;
    @Mock QuestionRepository questions;

    @InjectMocks QuestionProposalService service;

    private static final UUID AUTHOR_ID = UUID.fromString("aaaa0000-0000-0000-0000-000000000001");
    private static final UUID MOD_ID = UUID.fromString("bbbb0000-0000-0000-0000-000000000002");
    private static final UUID PROPOSAL_ID = UUID.fromString("cccc0000-0000-0000-0000-000000000003");

    @Test
    @DisplayName("Bloquea el envio si el usuario ya tiene 5 pendientes")
    void propose_limitReached_throwsValidation() {
        when(proposals.countExactDuplicate(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(0L);
        when(proposals.countByAuthorIdAndStatus(AUTHOR_ID, ProposalStatus.PENDING))
                .thenReturn((long) QuestionProposalService.PENDING_LIMIT);

        ProposeQuestionRequest request = new ProposeQuestionRequest(
                QuestionType.NUMERIC,
                "How many titles?",
                "3",
                null,
                "football",
                null);

        assertThatThrownBy(() -> service.propose(AUTHOR_ID, request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verify(proposals, never()).save(any());
    }

    @Test
    @DisplayName("Bloquea duplicados exactos que ya fueron rechazados")
    void propose_rejectedDuplicate_throwsConflict() {
        when(proposals.countExactDuplicate(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1L);

        ProposeQuestionRequest request = new ProposeQuestionRequest(
                QuestionType.BINARY,
                "Who has more goals?",
                "Player A",
                "Player B",
                "science",
                null);

        assertThatThrownBy(() -> service.propose(AUTHOR_ID, request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(proposals, never()).save(any());
    }

    @Test
    @DisplayName("Aprobar una propuesta numerica crea una pregunta activa")
    void approve_numericProposal_createsActiveQuestion() {
        QuestionProposal proposal = pendingProposal(QuestionType.NUMERIC, "42.5");
        when(proposals.findById(PROPOSAL_ID)).thenReturn(Optional.of(proposal));
        when(questions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(proposals.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.approve(PROPOSAL_ID, MOD_ID);

        ArgumentCaptor<Question> questionCaptor = ArgumentCaptor.forClass(Question.class);
        verify(questions).save(questionCaptor.capture());

        Question saved = questionCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(QuestionStatus.ACTIVE);
        assertThat(saved.getType()).isEqualTo(QuestionType.NUMERIC);
        assertThat(saved.getCorrectValue()).isEqualByComparingTo(new BigDecimal("42.5"));
        assertThat(saved.getTextHash()).hasSize(64);
    }

    @Test
    @DisplayName("Aprobar una propuesta binaria crea las dos opciones propuestas")
    void approve_binaryProposal_createsBinaryOptions() {
        QuestionProposal proposal = pendingProposal(QuestionType.BINARY, "Player A");
        proposal.setAlternativeAnswer("Player B");
        when(proposals.findById(PROPOSAL_ID)).thenReturn(Optional.of(proposal));
        when(questions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(proposals.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.approve(PROPOSAL_ID, MOD_ID);

        ArgumentCaptor<Question> questionCaptor = ArgumentCaptor.forClass(Question.class);
        verify(questions).save(questionCaptor.capture());

        Question saved = questionCaptor.getValue();
        assertThat(saved.getOptions()).hasSize(2);
        assertThat(saved.getOptions())
                .anySatisfy(option -> {
                    assertThat(option.getText()).isEqualTo("Player A");
                    assertThat(option.getIsCorrect()).isTrue();
                })
                .anySatisfy(option -> {
                    assertThat(option.getText()).isEqualTo("Player B");
                    assertThat(option.getIsCorrect()).isFalse();
                });
    }

    @Test
    @DisplayName("Rechazar registra moderador, fecha y motivo")
    void reject_setsReviewMetadata() {
        QuestionProposal proposal = pendingProposal(QuestionType.NUMERIC, "10");
        when(proposals.findById(PROPOSAL_ID)).thenReturn(Optional.of(proposal));
        when(proposals.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.reject(PROPOSAL_ID, MOD_ID, new RejectProposalRequest("Fuente no verificable"));

        ArgumentCaptor<QuestionProposal> proposalCaptor = ArgumentCaptor.forClass(QuestionProposal.class);
        verify(proposals).save(proposalCaptor.capture());

        QuestionProposal saved = proposalCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(saved.getReviewedBy()).isEqualTo(MOD_ID);
        assertThat(saved.getReviewedAt()).isNotNull();
        assertThat(saved.getRejectReason()).isEqualTo("Fuente no verificable");
    }

    private QuestionProposal pendingProposal(QuestionType type, String proposedAnswer) {
        return QuestionProposal.builder()
                .id(PROPOSAL_ID)
                .authorId(AUTHOR_ID)
                .type(type)
                .text("Community question?")
                .proposedAnswer(proposedAnswer)
                .category("general")
                .status(ProposalStatus.PENDING)
                .createdAt(Instant.now())
                .build();
    }
}
