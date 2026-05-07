package com.versus.api.scraping;

import com.versus.api.common.exception.ApiException;
import com.versus.api.common.exception.ErrorCode;
import com.versus.api.scraping.domain.Spider;
import com.versus.api.scraping.domain.SpiderRun;
import com.versus.api.scraping.dto.SpiderResponse;
import com.versus.api.scraping.dto.SpiderRunResponse;
import com.versus.api.scraping.repo.SpiderRepository;
import com.versus.api.scraping.repo.SpiderRunRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("SpiderService")
@ExtendWith(MockitoExtension.class)
class SpiderServiceTest {

    @Mock SpiderRepository spiders;
    @Mock SpiderRunRepository spiderRuns;

    @InjectMocks SpiderService spiderService;

    private static final UUID SPIDER_ID = UUID.fromString("aaaa0000-0000-0000-0000-000000000001");
    private static final UUID RUN_ID    = UUID.fromString("bbbb0000-0000-0000-0000-000000000002");

    // ── Factories ─────────────────────────────────────────────────────────────

    private Spider spider(String name, SpiderStatus status) {
        return Spider.builder()
                .id(SPIDER_ID)
                .name(name)
                .targetUrl("https://example.com/" + name)
                .status(status)
                .lastRunAt(null)
                .managedBy(null)
                .build();
    }

    private SpiderRun finishedRun() {
        return SpiderRun.builder()
                .id(RUN_ID)
                .spiderId(SPIDER_ID)
                .startedAt(Instant.parse("2026-01-01T10:00:00Z"))
                .finishedAt(Instant.parse("2026-01-01T10:05:00Z"))
                .questionsInserted(42)
                .errors(3)
                .build();
    }

    private SpiderRun runInProgress() {
        return SpiderRun.builder()
                .id(RUN_ID)
                .spiderId(SPIDER_ID)
                .startedAt(Instant.now())
                .questionsInserted(0)
                .errors(0)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // listSpiders
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("listSpiders")
    @Nested
    class ListSpiders {

        @DisplayName("Sin spiders registrados devuelve lista vacía")
        @Test
        void sinSpiders_devuelveListaVacia() {
            when(spiders.findAll()).thenReturn(List.of());

            assertThat(spiderService.listSpiders()).isEmpty();
        }

        @DisplayName("Un spider sin runs devuelve lastRun null")
        @Test
        void spiderSinRuns_lastRunEsNull() {
            when(spiders.findAll()).thenReturn(List.of(spider("rrss", SpiderStatus.IDLE)));
            when(spiderRuns.findFirstBySpiderIdOrderByStartedAtDesc(SPIDER_ID))
                    .thenReturn(Optional.empty());

            SpiderResponse res = spiderService.listSpiders().get(0);

            assertThat(res.lastRun()).isNull();
        }

        @DisplayName("Un spider con runs devuelve el último run en lastRun")
        @Test
        void spiderConRuns_lastRunPopulado() {
            SpiderRun run = finishedRun();
            when(spiders.findAll()).thenReturn(List.of(spider("rrss", SpiderStatus.IDLE)));
            when(spiderRuns.findFirstBySpiderIdOrderByStartedAtDesc(SPIDER_ID))
                    .thenReturn(Optional.of(run));

            SpiderResponse res = spiderService.listSpiders().get(0);

            assertThat(res.lastRun()).isNotNull();
            assertThat(res.lastRun().id()).isEqualTo(RUN_ID);
        }

        @DisplayName("Varios spiders devuelven una respuesta por cada uno")
        @Test
        void variosSpiders_devuelveUnoporCadaSpider() {
            Spider s1 = spider("rrss", SpiderStatus.IDLE);
            Spider s2 = Spider.builder().id(UUID.randomUUID()).name("futbol")
                    .targetUrl("https://fbref.com").status(SpiderStatus.FAILED)
                    .build();
            when(spiders.findAll()).thenReturn(List.of(s1, s2));
            when(spiderRuns.findFirstBySpiderIdOrderByStartedAtDesc(any()))
                    .thenReturn(Optional.empty());

            assertThat(spiderService.listSpiders()).hasSize(2);
        }

        @DisplayName("Los campos del SpiderResponse coinciden con los datos del spider")
        @Test
        void camposMapeadosCorrectamente() {
            Spider s = spider("rrss", SpiderStatus.IDLE);
            s.setLastRunAt(Instant.parse("2026-05-01T12:00:00Z"));
            when(spiders.findAll()).thenReturn(List.of(s));
            when(spiderRuns.findFirstBySpiderIdOrderByStartedAtDesc(SPIDER_ID))
                    .thenReturn(Optional.empty());

            SpiderResponse res = spiderService.listSpiders().get(0);

            assertThat(res.id()).isEqualTo(SPIDER_ID);
            assertThat(res.name()).isEqualTo("rrss");
            assertThat(res.targetUrl()).isEqualTo("https://example.com/rrss");
            assertThat(res.status()).isEqualTo(SpiderStatus.IDLE);
            assertThat(res.lastRunAt()).isEqualTo(Instant.parse("2026-05-01T12:00:00Z"));
        }

        @DisplayName("Los campos del SpiderRunResponse del lastRun son correctos")
        @Test
        void camposLastRunMapeadosCorrectamente() {
            SpiderRun run = finishedRun();
            when(spiders.findAll()).thenReturn(List.of(spider("rrss", SpiderStatus.IDLE)));
            when(spiderRuns.findFirstBySpiderIdOrderByStartedAtDesc(SPIDER_ID))
                    .thenReturn(Optional.of(run));

            SpiderRunResponse lastRun = spiderService.listSpiders().get(0).lastRun();

            assertThat(lastRun.startedAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
            assertThat(lastRun.finishedAt()).isEqualTo(Instant.parse("2026-01-01T10:05:00Z"));
            assertThat(lastRun.questionsInserted()).isEqualTo(42);
            assertThat(lastRun.errors()).isEqualTo(3);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // triggerRun
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("triggerRun")
    @Nested
    class TriggerRun {

        private SpiderService svc;

        @BeforeEach
        void setUp() {
            svc = spy(new SpiderService(spiders, spiderRuns));
            ReflectionTestUtils.setField(svc, "scraperWorkingDir", "/tmp/test-scraper");
            lenient().doNothing().when(svc).launchProcess(any(), any(), any());
        }

        @DisplayName("Spider no encontrado lanza NOT_FOUND")
        @Test
        void spiderNoEncontrado_lanzaNotFound() {
            when(spiders.findByName("desconocido")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> svc.triggerRun("desconocido"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode())
                            .isEqualTo(ErrorCode.NOT_FOUND));
        }

        @DisplayName("Spider ya en estado RUNNING lanza CONFLICT")
        @Test
        void spiderEnEstadoRunning_lanzaConflict() {
            when(spiders.findByName("rrss")).thenReturn(Optional.of(spider("rrss", SpiderStatus.RUNNING)));

            assertThatThrownBy(() -> svc.triggerRun("rrss"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode())
                            .isEqualTo(ErrorCode.CONFLICT));
        }

        @DisplayName("Spider en estado IDLE puede lanzarse sin conflicto")
        @Test
        void spiderEnEstadoIdle_puedeLanzarse() {
            when(spiders.findByName("rrss")).thenReturn(Optional.of(spider("rrss", SpiderStatus.IDLE)));
            when(spiders.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(spiderRuns.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThatCode(() -> svc.triggerRun("rrss")).doesNotThrowAnyException();
        }

        @DisplayName("Spider en estado FAILED puede relanzarse sin conflicto")
        @Test
        void spiderEnEstadoFailed_puedeRelanzarse() {
            when(spiders.findByName("rrss")).thenReturn(Optional.of(spider("rrss", SpiderStatus.FAILED)));
            when(spiders.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(spiderRuns.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThatCode(() -> svc.triggerRun("rrss")).doesNotThrowAnyException();
        }

        @DisplayName("El estado del spider se actualiza a RUNNING antes de devolver")
        @Test
        void caminoFeliz_estadoActualizadoARunning() {
            when(spiders.findByName("rrss")).thenReturn(Optional.of(spider("rrss", SpiderStatus.IDLE)));
            when(spiders.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(spiderRuns.save(any())).thenAnswer(inv -> inv.getArgument(0));

            svc.triggerRun("rrss");

            ArgumentCaptor<Spider> cap = ArgumentCaptor.forClass(Spider.class);
            verify(spiders).save(cap.capture());
            assertThat(cap.getValue().getStatus()).isEqualTo(SpiderStatus.RUNNING);
        }

        @DisplayName("El lastRunAt del spider se actualiza al lanzar")
        @Test
        void caminoFeliz_lastRunAtActualizado() {
            Spider s = spider("rrss", SpiderStatus.IDLE);
            when(spiders.findByName("rrss")).thenReturn(Optional.of(s));
            when(spiders.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(spiderRuns.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Instant before = Instant.now();
            svc.triggerRun("rrss");

            assertThat(s.getLastRunAt()).isNotNull();
            assertThat(s.getLastRunAt()).isAfterOrEqualTo(before);
        }

        @DisplayName("El SpiderRun se crea con el spiderId del spider encontrado")
        @Test
        void caminoFeliz_spiderRunCreadoConSpiderId() {
            when(spiders.findByName("rrss")).thenReturn(Optional.of(spider("rrss", SpiderStatus.IDLE)));
            when(spiders.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(spiderRuns.save(any())).thenAnswer(inv -> inv.getArgument(0));

            svc.triggerRun("rrss");

            ArgumentCaptor<SpiderRun> cap = ArgumentCaptor.forClass(SpiderRun.class);
            verify(spiderRuns).save(cap.capture());
            assertThat(cap.getValue().getSpiderId()).isEqualTo(SPIDER_ID);
        }

        @DisplayName("El SpiderRun inicial tiene questionsInserted=0")
        @Test
        void caminoFeliz_questionsInsertedInicialEsCero() {
            when(spiders.findByName("rrss")).thenReturn(Optional.of(spider("rrss", SpiderStatus.IDLE)));
            when(spiders.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(spiderRuns.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SpiderRunResponse res = svc.triggerRun("rrss");

            assertThat(res.questionsInserted()).isZero();
        }

        @DisplayName("El SpiderRun inicial tiene errors=0")
        @Test
        void caminoFeliz_errorsInicialesEsCero() {
            when(spiders.findByName("rrss")).thenReturn(Optional.of(spider("rrss", SpiderStatus.IDLE)));
            when(spiders.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(spiderRuns.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SpiderRunResponse res = svc.triggerRun("rrss");

            assertThat(res.errors()).isZero();
        }

        @DisplayName("La respuesta contiene startedAt no nulo")
        @Test
        void caminoFeliz_respuestaConStartedAtNoNull() {
            when(spiders.findByName("rrss")).thenReturn(Optional.of(spider("rrss", SpiderStatus.IDLE)));
            when(spiders.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(spiderRuns.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SpiderRunResponse res = svc.triggerRun("rrss");

            assertThat(res.startedAt()).isNotNull();
        }

        @DisplayName("La respuesta tiene finishedAt null (el proceso aún no ha terminado)")
        @Test
        void caminoFeliz_finishedAtInicialmenteNull() {
            when(spiders.findByName("rrss")).thenReturn(Optional.of(spider("rrss", SpiderStatus.IDLE)));
            when(spiders.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(spiderRuns.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SpiderRunResponse res = svc.triggerRun("rrss");

            assertThat(res.finishedAt()).isNull();
        }

        @DisplayName("launchProcess se invoca con el spiderId, runId y nombre correctos")
        @Test
        void caminoFeliz_launchProcessInvocadoConParametrosCorrectos() {
            when(spiders.findByName("rrss")).thenReturn(Optional.of(spider("rrss", SpiderStatus.IDLE)));
            when(spiders.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(spiderRuns.save(any())).thenAnswer(inv -> inv.getArgument(0));

            svc.triggerRun("rrss");

            verify(svc).launchProcess(eq(SPIDER_ID), any(), eq("rrss"));
        }

        @DisplayName("El SpiderRun se persiste en el repositorio exactamente una vez")
        @Test
        void caminoFeliz_spiderRunGuardadoExactamenteUnaVez() {
            when(spiders.findByName("rrss")).thenReturn(Optional.of(spider("rrss", SpiderStatus.IDLE)));
            when(spiders.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(spiderRuns.save(any())).thenAnswer(inv -> inv.getArgument(0));

            svc.triggerRun("rrss");

            verify(spiderRuns, times(1)).save(any(SpiderRun.class));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // getRunHistory
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("getRunHistory")
    @Nested
    class GetRunHistory {

        @DisplayName("Spider no encontrado lanza NOT_FOUND")
        @Test
        void spiderNoEncontrado_lanzaNotFound() {
            when(spiders.findByName("desconocido")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> spiderService.getRunHistory("desconocido"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode())
                            .isEqualTo(ErrorCode.NOT_FOUND));
        }

        @DisplayName("Sin runs devuelve lista vacía")
        @Test
        void sinRuns_devuelveListaVacia() {
            when(spiders.findByName("rrss")).thenReturn(Optional.of(spider("rrss", SpiderStatus.IDLE)));
            when(spiderRuns.findBySpiderIdOrderByStartedAtDesc(SPIDER_ID)).thenReturn(List.of());

            assertThat(spiderService.getRunHistory("rrss")).isEmpty();
        }

        @DisplayName("Con runs devuelve un SpiderRunResponse por cada run")
        @Test
        void conRuns_devuelveUnoporCadaRun() {
            SpiderRun r1 = finishedRun();
            SpiderRun r2 = SpiderRun.builder().id(UUID.randomUUID()).spiderId(SPIDER_ID)
                    .startedAt(Instant.parse("2026-01-02T10:00:00Z"))
                    .finishedAt(Instant.parse("2026-01-02T10:03:00Z"))
                    .questionsInserted(7).errors(0).build();
            when(spiders.findByName("rrss")).thenReturn(Optional.of(spider("rrss", SpiderStatus.IDLE)));
            when(spiderRuns.findBySpiderIdOrderByStartedAtDesc(SPIDER_ID)).thenReturn(List.of(r1, r2));

            assertThat(spiderService.getRunHistory("rrss")).hasSize(2);
        }

        @DisplayName("Los campos del SpiderRunResponse coinciden con los datos del run")
        @Test
        void camposMapeadosCorrectamente() {
            SpiderRun run = finishedRun();
            when(spiders.findByName("rrss")).thenReturn(Optional.of(spider("rrss", SpiderStatus.IDLE)));
            when(spiderRuns.findBySpiderIdOrderByStartedAtDesc(SPIDER_ID)).thenReturn(List.of(run));

            SpiderRunResponse res = spiderService.getRunHistory("rrss").get(0);

            assertThat(res.id()).isEqualTo(RUN_ID);
            assertThat(res.startedAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
            assertThat(res.finishedAt()).isEqualTo(Instant.parse("2026-01-01T10:05:00Z"));
            assertThat(res.questionsInserted()).isEqualTo(42);
            assertThat(res.errors()).isEqualTo(3);
        }

        @DisplayName("Un run activo (finishedAt null) se mapea con finishedAt null")
        @Test
        void runActivoConFinishedAtNull_seMapeaComoNull() {
            SpiderRun active = runInProgress();
            when(spiders.findByName("rrss")).thenReturn(Optional.of(spider("rrss", SpiderStatus.RUNNING)));
            when(spiderRuns.findBySpiderIdOrderByStartedAtDesc(SPIDER_ID)).thenReturn(List.of(active));

            SpiderRunResponse res = spiderService.getRunHistory("rrss").get(0);

            assertThat(res.finishedAt()).isNull();
        }

        @DisplayName("El orden de los runs es delegado al repositorio")
        @Test
        void ordenDelegadoAlRepositorio() {
            when(spiders.findByName("rrss")).thenReturn(Optional.of(spider("rrss", SpiderStatus.IDLE)));
            when(spiderRuns.findBySpiderIdOrderByStartedAtDesc(SPIDER_ID)).thenReturn(List.of());

            spiderService.getRunHistory("rrss");

            verify(spiderRuns).findBySpiderIdOrderByStartedAtDesc(SPIDER_ID);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // launchProcess
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("launchProcess")
    @Nested
    class LaunchProcessTests {

        @BeforeEach
        void setUp() {
            ReflectionTestUtils.setField(spiderService, "scraperWorkingDir", "/tmp/test-scraper");
        }

        @DisplayName("Run no encontrado: retorna silenciosamente sin guardar nada")
        @Test
        void runNoEncontrado_retornaSilenciosamente() {
            when(spiderRuns.findById(RUN_ID)).thenReturn(Optional.empty());
            when(spiders.findById(SPIDER_ID)).thenReturn(Optional.of(spider("rrss", SpiderStatus.RUNNING)));

            spiderService.launchProcess(SPIDER_ID, RUN_ID, "rrss");

            verify(spiderRuns, never()).save(any());
            verify(spiders, never()).save(any());
        }

        @DisplayName("Spider no encontrado: retorna silenciosamente sin guardar nada")
        @Test
        void spiderNoEncontrado_retornaSilenciosamente() {
            when(spiderRuns.findById(RUN_ID)).thenReturn(Optional.of(runInProgress()));
            when(spiders.findById(SPIDER_ID)).thenReturn(Optional.empty());

            spiderService.launchProcess(SPIDER_ID, RUN_ID, "rrss");

            verify(spiderRuns, never()).save(any());
            verify(spiders, never()).save(any());
        }

        @DisplayName("exitCode=0: estado del spider pasa a IDLE")
        @Test
        void exitCodeCero_estableceSpiderIdle() throws Exception {
            Spider s = spider("rrss", SpiderStatus.RUNNING);
            SpiderRun run = runInProgress();
            when(spiderRuns.findById(RUN_ID)).thenReturn(Optional.of(run));
            when(spiders.findById(SPIDER_ID)).thenReturn(Optional.of(s));

            Process mockProcess = mock(Process.class);
            when(mockProcess.waitFor()).thenReturn(0);

            try (MockedConstruction<ProcessBuilder> ignored = mockConstruction(ProcessBuilder.class,
                    (mock, ctx) -> when(mock.start()).thenReturn(mockProcess))) {
                spiderService.launchProcess(SPIDER_ID, RUN_ID, "rrss");
            }

            assertThat(s.getStatus()).isEqualTo(SpiderStatus.IDLE);
            verify(spiders).save(s);
        }

        @DisplayName("exitCode!=0: estado del spider pasa a FAILED")
        @Test
        void exitCodeNoCero_estableceSpiderFailed() throws Exception {
            Spider s = spider("rrss", SpiderStatus.RUNNING);
            SpiderRun run = runInProgress();
            when(spiderRuns.findById(RUN_ID)).thenReturn(Optional.of(run));
            when(spiders.findById(SPIDER_ID)).thenReturn(Optional.of(s));

            Process mockProcess = mock(Process.class);
            when(mockProcess.waitFor()).thenReturn(1);

            try (MockedConstruction<ProcessBuilder> ignored = mockConstruction(ProcessBuilder.class,
                    (mock, ctx) -> when(mock.start()).thenReturn(mockProcess))) {
                spiderService.launchProcess(SPIDER_ID, RUN_ID, "rrss");
            }

            assertThat(s.getStatus()).isEqualTo(SpiderStatus.FAILED);
        }

        @DisplayName("IOException al arrancar el proceso: spider queda en FAILED")
        @Test
        void ioException_estableceSpiderFailed() throws Exception {
            Spider s = spider("rrss", SpiderStatus.RUNNING);
            SpiderRun run = runInProgress();
            when(spiderRuns.findById(RUN_ID)).thenReturn(Optional.of(run));
            when(spiders.findById(SPIDER_ID)).thenReturn(Optional.of(s));

            try (MockedConstruction<ProcessBuilder> ignored = mockConstruction(ProcessBuilder.class,
                    (mock, ctx) -> when(mock.start()).thenThrow(new IOException("scrapy not found")))) {
                spiderService.launchProcess(SPIDER_ID, RUN_ID, "rrss");
            }

            assertThat(s.getStatus()).isEqualTo(SpiderStatus.FAILED);
        }

        @DisplayName("finishedAt siempre se establece aunque el proceso falle (bloque finally)")
        @Test
        void finishedAt_siempreEstablecido_aunqueFalle() throws Exception {
            SpiderRun run = runInProgress();
            assertThat(run.getFinishedAt()).isNull();

            Spider s = spider("rrss", SpiderStatus.RUNNING);
            when(spiderRuns.findById(RUN_ID)).thenReturn(Optional.of(run));
            when(spiders.findById(SPIDER_ID)).thenReturn(Optional.of(s));

            try (MockedConstruction<ProcessBuilder> ignored = mockConstruction(ProcessBuilder.class,
                    (mock, ctx) -> when(mock.start()).thenThrow(new IOException("fail")))) {
                spiderService.launchProcess(SPIDER_ID, RUN_ID, "rrss");
            }

            assertThat(run.getFinishedAt()).isNotNull();
            verify(spiderRuns).save(run);
        }

        @DisplayName("finishedAt se establece también en el camino feliz")
        @Test
        void finishedAt_establecidoEnCaminoFeliz() throws Exception {
            SpiderRun run = runInProgress();
            Spider s = spider("rrss", SpiderStatus.RUNNING);
            when(spiderRuns.findById(RUN_ID)).thenReturn(Optional.of(run));
            when(spiders.findById(SPIDER_ID)).thenReturn(Optional.of(s));

            Process mockProcess = mock(Process.class);
            when(mockProcess.waitFor()).thenReturn(0);

            try (MockedConstruction<ProcessBuilder> ignored = mockConstruction(ProcessBuilder.class,
                    (mock, ctx) -> when(mock.start()).thenReturn(mockProcess))) {
                spiderService.launchProcess(SPIDER_ID, RUN_ID, "rrss");
            }

            assertThat(run.getFinishedAt()).isNotNull();
        }

        @DisplayName("El run se persiste en el repositorio tras la ejecución")
        @Test
        void run_persistidoTrasEjecucion() throws Exception {
            SpiderRun run = runInProgress();
            Spider s = spider("rrss", SpiderStatus.RUNNING);
            when(spiderRuns.findById(RUN_ID)).thenReturn(Optional.of(run));
            when(spiders.findById(SPIDER_ID)).thenReturn(Optional.of(s));

            Process mockProcess = mock(Process.class);
            when(mockProcess.waitFor()).thenReturn(0);

            try (MockedConstruction<ProcessBuilder> ignored = mockConstruction(ProcessBuilder.class,
                    (mock, ctx) -> when(mock.start()).thenReturn(mockProcess))) {
                spiderService.launchProcess(SPIDER_ID, RUN_ID, "rrss");
            }

            verify(spiderRuns).save(run);
        }
    }
}
