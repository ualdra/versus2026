package com.versus.api.moderation;

import com.versus.api.common.exception.ApiException;
import com.versus.api.common.exception.ErrorCode;
import com.versus.api.moderation.domain.QuestionReport;
import com.versus.api.moderation.dto.ReportRequest;
import com.versus.api.moderation.dto.ReportResponse;
import com.versus.api.moderation.dto.ResolveRequest;
import com.versus.api.moderation.repo.QuestionReportRepository;
import com.versus.api.questions.QuestionStatus;
import com.versus.api.questions.QuestionType;
import com.versus.api.questions.domain.Question;
import com.versus.api.questions.repo.QuestionRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("ModerationService")
@ExtendWith(MockitoExtension.class)
class ModerationServiceTest {

    @Mock QuestionReportRepository reports;
    @Mock QuestionRepository questions;

    @InjectMocks ModerationService service;

    private static final UUID USER_ID     = UUID.fromString("aaaa0000-0000-0000-0000-000000000001");
    private static final UUID MOD_ID      = UUID.fromString("bbbb0000-0000-0000-0000-000000000002");
    private static final UUID QUESTION_ID = UUID.fromString("cccc0000-0000-0000-0000-000000000003");
    private static final UUID REPORT_ID   = UUID.fromString("dddd0000-0000-0000-0000-000000000004");

    // ── Factories ─────────────────────────────────────────────────────────────

    private Question activeQuestion() {
        return Question.builder()
                .id(QUESTION_ID)
                .text("¿Quién ganó el mundial de 2022?")
                .type(QuestionType.BINARY)
                .category("FOOTBALL")
                .status(QuestionStatus.ACTIVE)
                .build();
    }

    private QuestionReport pendingReport() {
        return QuestionReport.builder()
                .id(REPORT_ID)
                .questionId(QUESTION_ID)
                .reportedBy(USER_ID)
                .reason(ReportReason.WRONG_ANSWER)
                .status(ReportStatus.PENDING)
                .createdAt(Instant.now())
                .build();
    }

    private ReportRequest reportRequest(ReportReason reason) {
        return new ReportRequest(reason, "comentario opcional");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // report
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("report")
    @Nested
    class Report {

        @DisplayName("Camino feliz: crea el reporte y devuelve ReportResponse")
        @Test
        void caminoFeliz_creaReporteYDevuelveResponse() {
            when(questions.findById(QUESTION_ID)).thenReturn(Optional.of(activeQuestion()));
            when(reports.existsByQuestionIdAndReportedByAndStatus(QUESTION_ID, USER_ID, ReportStatus.PENDING))
                    .thenReturn(false);
            when(reports.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(reports.countByQuestionIdAndStatus(QUESTION_ID, ReportStatus.PENDING)).thenReturn(1L);

            ReportResponse res = service.report(QUESTION_ID, USER_ID, reportRequest(ReportReason.WRONG_ANSWER));

            assertThat(res).isNotNull();
            assertThat(res.questionId()).isEqualTo(QUESTION_ID);
            assertThat(res.reason()).isEqualTo(ReportReason.WRONG_ANSWER);
            assertThat(res.status()).isEqualTo(ReportStatus.PENDING);
        }

        @DisplayName("El reporte se persiste con los campos correctos")
        @Test
        void caminoFeliz_reportePersistidoConCamposCorrectos() {
            when(questions.findById(QUESTION_ID)).thenReturn(Optional.of(activeQuestion()));
            when(reports.existsByQuestionIdAndReportedByAndStatus(any(), any(), any())).thenReturn(false);
            when(reports.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(reports.countByQuestionIdAndStatus(any(), any())).thenReturn(1L);

            service.report(QUESTION_ID, USER_ID, reportRequest(ReportReason.OUTDATED));

            ArgumentCaptor<QuestionReport> cap = ArgumentCaptor.forClass(QuestionReport.class);
            verify(reports).save(cap.capture());
            QuestionReport saved = cap.getValue();
            assertThat(saved.getQuestionId()).isEqualTo(QUESTION_ID);
            assertThat(saved.getReportedBy()).isEqualTo(USER_ID);
            assertThat(saved.getReason()).isEqualTo(ReportReason.OUTDATED);
            assertThat(saved.getComment()).isEqualTo("comentario opcional");
        }

        @DisplayName("Pregunta no encontrada lanza NOT_FOUND")
        @Test
        void preguntaNoEncontrada_lanzaNotFound() {
            when(questions.findById(QUESTION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.report(QUESTION_ID, USER_ID, reportRequest(ReportReason.OTHER)))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.NOT_FOUND));
        }

        @DisplayName("Pregunta INACTIVE lanza NOT_FOUND (no expone que existe)")
        @Test
        void preguntaInactiva_lanzaNotFound() {
            Question inactive = activeQuestion();
            inactive.setStatus(QuestionStatus.INACTIVE);
            when(questions.findById(QUESTION_ID)).thenReturn(Optional.of(inactive));

            assertThatThrownBy(() -> service.report(QUESTION_ID, USER_ID, reportRequest(ReportReason.OTHER)))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.NOT_FOUND));
        }

        @DisplayName("Reporte duplicado pendiente del mismo usuario lanza CONFLICT")
        @Test
        void reporteDuplicado_lanzaConflict() {
            when(questions.findById(QUESTION_ID)).thenReturn(Optional.of(activeQuestion()));
            when(reports.existsByQuestionIdAndReportedByAndStatus(QUESTION_ID, USER_ID, ReportStatus.PENDING))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.report(QUESTION_ID, USER_ID, reportRequest(ReportReason.OTHER)))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.CONFLICT));
        }

        @DisplayName("Reporte duplicado: no se llega a persistir el reporte")
        @Test
        void reporteDuplicado_noGuardaReporte() {
            when(questions.findById(QUESTION_ID)).thenReturn(Optional.of(activeQuestion()));
            when(reports.existsByQuestionIdAndReportedByAndStatus(any(), any(), any())).thenReturn(true);

            assertThatThrownBy(() -> service.report(QUESTION_ID, USER_ID, reportRequest(ReportReason.OTHER)))
                    .isInstanceOf(ApiException.class);

            verify(reports, never()).save(any());
        }

        @DisplayName("La respuesta incluye el texto de la pregunta como snapshot")
        @Test
        void respuesta_incluyeSnapshotDePregunta() {
            when(questions.findById(QUESTION_ID)).thenReturn(Optional.of(activeQuestion()));
            when(reports.existsByQuestionIdAndReportedByAndStatus(any(), any(), any())).thenReturn(false);
            when(reports.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(reports.countByQuestionIdAndStatus(any(), any())).thenReturn(1L);

            ReportResponse res = service.report(QUESTION_ID, USER_ID, reportRequest(ReportReason.WRONG_ANSWER));

            assertThat(res.questionText()).isEqualTo("¿Quién ganó el mundial de 2022?");
            assertThat(res.questionType()).isEqualTo("BINARY");
            assertThat(res.questionCategory()).isEqualTo("FOOTBALL");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Auto-flag
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("Auto-flag")
    @Nested
    class AutoFlag {

        @DisplayName("Con exactamente 5 reportes pendientes la pregunta pasa a FLAGGED")
        @Test
        void cincoReportes_preguntaPasaAFlagged() {
            when(questions.findById(QUESTION_ID)).thenReturn(Optional.of(activeQuestion()));
            when(reports.existsByQuestionIdAndReportedByAndStatus(any(), any(), any())).thenReturn(false);
            when(reports.save(any(QuestionReport.class))).thenAnswer(inv -> inv.getArgument(0));
            when(reports.countByQuestionIdAndStatus(QUESTION_ID, ReportStatus.PENDING))
                    .thenReturn((long) ModerationService.REPORT_FLAG_THRESHOLD);

            service.report(QUESTION_ID, USER_ID, reportRequest(ReportReason.WRONG_ANSWER));

            ArgumentCaptor<Question> cap = ArgumentCaptor.forClass(Question.class);
            verify(questions).save(cap.capture());
            assertThat(cap.getValue().getStatus()).isEqualTo(QuestionStatus.FLAGGED);
        }

        @DisplayName("Con más de 5 reportes la pregunta también pasa a FLAGGED")
        @Test
        void masDeCincoReportes_preguntaPasaAFlagged() {
            when(questions.findById(QUESTION_ID)).thenReturn(Optional.of(activeQuestion()));
            when(reports.existsByQuestionIdAndReportedByAndStatus(any(), any(), any())).thenReturn(false);
            when(reports.save(any(QuestionReport.class))).thenAnswer(inv -> inv.getArgument(0));
            when(reports.countByQuestionIdAndStatus(QUESTION_ID, ReportStatus.PENDING)).thenReturn(10L);

            service.report(QUESTION_ID, USER_ID, reportRequest(ReportReason.WRONG_ANSWER));

            ArgumentCaptor<Question> cap = ArgumentCaptor.forClass(Question.class);
            verify(questions).save(cap.capture());
            assertThat(cap.getValue().getStatus()).isEqualTo(QuestionStatus.FLAGGED);
        }

        @DisplayName("Con menos de 5 reportes la pregunta no se modifica")
        @Test
        void menosDeCincoReportes_preguntaNoSeFlagea() {
            when(questions.findById(QUESTION_ID)).thenReturn(Optional.of(activeQuestion()));
            when(reports.existsByQuestionIdAndReportedByAndStatus(any(), any(), any())).thenReturn(false);
            when(reports.save(any(QuestionReport.class))).thenAnswer(inv -> inv.getArgument(0));
            when(reports.countByQuestionIdAndStatus(QUESTION_ID, ReportStatus.PENDING))
                    .thenReturn((long) ModerationService.REPORT_FLAG_THRESHOLD - 1);

            service.report(QUESTION_ID, USER_ID, reportRequest(ReportReason.WRONG_ANSWER));

            verify(questions, never()).save(any());
        }

        @DisplayName("Una pregunta ya FLAGGED no se vuelve a guardar aunque supere el umbral")
        @Test
        void preguntaYaFlagged_noSeGuardaDeNuevo() {
            Question flagged = activeQuestion();
            flagged.setStatus(QuestionStatus.FLAGGED);
            when(questions.findById(QUESTION_ID)).thenReturn(Optional.of(flagged));
            when(reports.existsByQuestionIdAndReportedByAndStatus(any(), any(), any())).thenReturn(false);
            when(reports.save(any(QuestionReport.class))).thenAnswer(inv -> inv.getArgument(0));
            when(reports.countByQuestionIdAndStatus(any(), any())).thenReturn(10L);

            service.report(QUESTION_ID, USER_ID, reportRequest(ReportReason.WRONG_ANSWER));

            verify(questions, never()).save(any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // listReports
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("listReports")
    @Nested
    class ListReports {

        @DisplayName("Sin filtro de estado devuelve todos los reportes")
        @Test
        void sinFiltro_devuelveTodos() {
            Pageable pageable = PageRequest.of(0, 20);
            QuestionReport r = pendingReport();
            when(reports.findAllByOrderByCreatedAtDesc(pageable))
                    .thenReturn(new PageImpl<>(List.of(r)));
            when(questions.findById(QUESTION_ID)).thenReturn(Optional.of(activeQuestion()));

            Page<ReportResponse> result = service.listReports(null, pageable);

            assertThat(result.getContent()).hasSize(1);
            verify(reports).findAllByOrderByCreatedAtDesc(pageable);
            verify(reports, never()).findByStatusOrderByCreatedAtDesc(any(), any());
        }

        @DisplayName("Con filtro de estado delega al método filtrado del repositorio")
        @Test
        void conFiltro_delegaAlMetodoFiltrado() {
            Pageable pageable = PageRequest.of(0, 20);
            when(reports.findByStatusOrderByCreatedAtDesc(ReportStatus.PENDING, pageable))
                    .thenReturn(new PageImpl<>(List.of(pendingReport())));
            when(questions.findById(QUESTION_ID)).thenReturn(Optional.of(activeQuestion()));

            service.listReports(ReportStatus.PENDING, pageable);

            verify(reports).findByStatusOrderByCreatedAtDesc(ReportStatus.PENDING, pageable);
            verify(reports, never()).findAllByOrderByCreatedAtDesc(any());
        }

        @DisplayName("Sin reportes devuelve página vacía")
        @Test
        void sinReportes_devuelvePaginaVacia() {
            Pageable pageable = PageRequest.of(0, 20);
            when(reports.findByStatusOrderByCreatedAtDesc(ReportStatus.PENDING, pageable))
                    .thenReturn(Page.empty());

            Page<ReportResponse> result = service.listReports(ReportStatus.PENDING, pageable);

            assertThat(result.getContent()).isEmpty();
        }

        @DisplayName("El ReportResponse incluye el snapshot de la pregunta")
        @Test
        void response_incluyeSnapshotPregunta() {
            Pageable pageable = PageRequest.of(0, 20);
            when(reports.findByStatusOrderByCreatedAtDesc(eq(ReportStatus.PENDING), any()))
                    .thenReturn(new PageImpl<>(List.of(pendingReport())));
            when(questions.findById(QUESTION_ID)).thenReturn(Optional.of(activeQuestion()));

            ReportResponse res = service.listReports(ReportStatus.PENDING, pageable).getContent().get(0);

            assertThat(res.questionText()).isEqualTo("¿Quién ganó el mundial de 2022?");
            assertThat(res.questionType()).isEqualTo("BINARY");
        }

        @DisplayName("Si la pregunta ya no existe el snapshot queda null sin lanzar excepción")
        @Test
        void preguntaEliminada_snapshotNullSinExcepcion() {
            Pageable pageable = PageRequest.of(0, 20);
            when(reports.findAllByOrderByCreatedAtDesc(pageable))
                    .thenReturn(new PageImpl<>(List.of(pendingReport())));
            when(questions.findById(QUESTION_ID)).thenReturn(Optional.empty());

            ReportResponse res = service.listReports(null, pageable).getContent().get(0);

            assertThat(res.questionText()).isNull();
            assertThat(res.questionType()).isNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // resolve
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("resolve")
    @Nested
    class Resolve {

        @DisplayName("DISMISS: reporte pasa a DISMISSED y no toca la pregunta")
        @Test
        void dismiss_reporteDissmisedSinTocarPregunta() {
            QuestionReport report = pendingReport();
            when(reports.findById(REPORT_ID)).thenReturn(Optional.of(report));
            when(questions.findById(QUESTION_ID)).thenReturn(Optional.of(activeQuestion()));
            when(reports.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ReportResponse res = service.resolve(REPORT_ID, MOD_ID, new ResolveRequest(ResolveAction.DISMISS));

            assertThat(res.status()).isEqualTo(ReportStatus.DISMISSED);
            assertThat(res.action()).isEqualTo(ResolveAction.DISMISS);
            verify(questions, never()).save(any());
        }

        @DisplayName("DISMISS: resolvedBy y resolvedAt se establecen correctamente")
        @Test
        void dismiss_resolvedByYResolvedAtEstablecidos() {
            QuestionReport report = pendingReport();
            when(reports.findById(REPORT_ID)).thenReturn(Optional.of(report));
            when(questions.findById(QUESTION_ID)).thenReturn(Optional.of(activeQuestion()));
            when(reports.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Instant before = Instant.now();
            service.resolve(REPORT_ID, MOD_ID, new ResolveRequest(ResolveAction.DISMISS));

            ArgumentCaptor<QuestionReport> cap = ArgumentCaptor.forClass(QuestionReport.class);
            verify(reports).save(cap.capture());
            assertThat(cap.getValue().getResolvedBy()).isEqualTo(MOD_ID);
            assertThat(cap.getValue().getResolvedAt()).isAfterOrEqualTo(before);
        }

        @DisplayName("DELETE_QUESTION: reporte pasa a RESOLVED y la pregunta a INACTIVE")
        @Test
        void deleteQuestion_reporteResolvedYPreguntaInactive() {
            QuestionReport report = pendingReport();
            Question question = activeQuestion();
            when(reports.findById(REPORT_ID)).thenReturn(Optional.of(report));
            when(questions.findById(QUESTION_ID)).thenReturn(Optional.of(question));
            when(reports.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ReportResponse res = service.resolve(REPORT_ID, MOD_ID, new ResolveRequest(ResolveAction.DELETE_QUESTION));

            assertThat(res.status()).isEqualTo(ReportStatus.RESOLVED);
            ArgumentCaptor<Question> cap = ArgumentCaptor.forClass(Question.class);
            verify(questions).save(cap.capture());
            assertThat(cap.getValue().getStatus()).isEqualTo(QuestionStatus.INACTIVE);
        }

        @DisplayName("DELETE_QUESTION: la pregunta no se borra de BD, solo se marca INACTIVE")
        @Test
        void deleteQuestion_noBorraDeBD() {
            when(reports.findById(REPORT_ID)).thenReturn(Optional.of(pendingReport()));
            when(questions.findById(QUESTION_ID)).thenReturn(Optional.of(activeQuestion()));
            when(reports.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.resolve(REPORT_ID, MOD_ID, new ResolveRequest(ResolveAction.DELETE_QUESTION));

            verify(questions, never()).deleteById(any());
        }

        @DisplayName("EDIT_QUESTION: reporte pasa a RESOLVED pero la pregunta no se modifica")
        @Test
        void editQuestion_reporteResolvedSinModificarPregunta() {
            when(reports.findById(REPORT_ID)).thenReturn(Optional.of(pendingReport()));
            when(questions.findById(QUESTION_ID)).thenReturn(Optional.of(activeQuestion()));
            when(reports.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ReportResponse res = service.resolve(REPORT_ID, MOD_ID, new ResolveRequest(ResolveAction.EDIT_QUESTION));

            assertThat(res.status()).isEqualTo(ReportStatus.RESOLVED);
            verify(questions, never()).save(any());
        }

        @DisplayName("Reporte no encontrado lanza NOT_FOUND")
        @Test
        void reporteNoEncontrado_lanzaNotFound() {
            when(reports.findById(REPORT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.resolve(REPORT_ID, MOD_ID, new ResolveRequest(ResolveAction.DISMISS)))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.NOT_FOUND));
        }

        @DisplayName("Reporte ya resuelto (DISMISSED) lanza CONFLICT")
        @Test
        void reporteYaDismissed_lanzaConflict() {
            QuestionReport already = pendingReport();
            already.setStatus(ReportStatus.DISMISSED);
            when(reports.findById(REPORT_ID)).thenReturn(Optional.of(already));

            assertThatThrownBy(() -> service.resolve(REPORT_ID, MOD_ID, new ResolveRequest(ResolveAction.DISMISS)))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.CONFLICT));
        }

        @DisplayName("Reporte ya resuelto (RESOLVED) lanza CONFLICT")
        @Test
        void reporteYaResolved_lanzaConflict() {
            QuestionReport already = pendingReport();
            already.setStatus(ReportStatus.RESOLVED);
            when(reports.findById(REPORT_ID)).thenReturn(Optional.of(already));

            assertThatThrownBy(() -> service.resolve(REPORT_ID, MOD_ID, new ResolveRequest(ResolveAction.DISMISS)))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.CONFLICT));
        }

        @DisplayName("DELETE_QUESTION con pregunta ya inexistente no lanza excepción")
        @Test
        void deleteQuestion_preguntaInexistente_noLanzaExcepcion() {
            when(reports.findById(REPORT_ID)).thenReturn(Optional.of(pendingReport()));
            when(questions.findById(QUESTION_ID)).thenReturn(Optional.empty());
            when(reports.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThatCode(() ->
                    service.resolve(REPORT_ID, MOD_ID, new ResolveRequest(ResolveAction.DELETE_QUESTION)))
                    .doesNotThrowAnyException();
        }
    }
}
