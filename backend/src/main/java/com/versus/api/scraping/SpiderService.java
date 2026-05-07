package com.versus.api.scraping;

import com.versus.api.common.exception.ApiException;
import com.versus.api.scraping.domain.Spider;
import com.versus.api.scraping.domain.SpiderRun;
import com.versus.api.scraping.dto.SpiderResponse;
import com.versus.api.scraping.dto.SpiderRunResponse;
import com.versus.api.scraping.repo.SpiderRepository;
import com.versus.api.scraping.repo.SpiderRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpiderService {

    private final SpiderRepository spiders;
    private final SpiderRunRepository spiderRuns;

    @Value("${scraper.working-dir:../scraper}")
    private String scraperWorkingDir;

    // ── List ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SpiderResponse> listSpiders() {
        return spiders.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    // ── Run ───────────────────────────────────────────────────────────────────

    @Transactional
    public SpiderRunResponse triggerRun(String name) {
        Spider spider = spiders.findByName(name)
                .orElseThrow(() -> ApiException.notFound("Spider not found: " + name));

        if (spider.getStatus() == SpiderStatus.RUNNING) {
            throw ApiException.conflict("Spider '" + name + "' is already running");
        }

        spider.setStatus(SpiderStatus.RUNNING);
        spider.setLastRunAt(Instant.now());
        spiders.save(spider);

        SpiderRun run = SpiderRun.builder()
                .spiderId(spider.getId())
                .startedAt(Instant.now())
                .questionsInserted(0)
                .errors(0)
                .build();
        spiderRuns.save(run);

        launchProcess(spider.getId(), run.getId(), name);

        return toRunResponse(run);
    }

    // ── Run history ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SpiderRunResponse> getRunHistory(String name) {
        Spider spider = spiders.findByName(name)
                .orElseThrow(() -> ApiException.notFound("Spider not found: " + name));
        return spiderRuns.findBySpiderIdOrderByStartedAtDesc(spider.getId())
                .stream()
                .map(this::toRunResponse)
                .toList();
    }

    // ── Async process ─────────────────────────────────────────────────────────

    @Async
    protected void launchProcess(UUID spiderId, UUID runId, String spiderName) {
        SpiderRun run = spiderRuns.findById(runId).orElse(null);
        Spider spider = spiders.findById(spiderId).orElse(null);
        if (run == null || spider == null) return;

        int exitCode = -1;
        try {
            ProcessBuilder pb = new ProcessBuilder("scrapy", "crawl", spiderName);
            pb.directory(new java.io.File(scraperWorkingDir));
            pb.redirectErrorStream(true);
            Process process = pb.start();
            exitCode = process.waitFor();
        } catch (IOException | InterruptedException e) {
            log.error("Error launching spider '{}': {}", spiderName, e.getMessage());
            Thread.currentThread().interrupt();
        } finally {
            run.setFinishedAt(Instant.now());
            spider.setStatus(exitCode == 0 ? SpiderStatus.IDLE : SpiderStatus.FAILED);
            spiderRuns.save(run);
            spiders.save(spider);
        }
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private SpiderResponse toResponse(Spider spider) {
        SpiderRun lastRun = spiderRuns
                .findFirstBySpiderIdOrderByStartedAtDesc(spider.getId())
                .orElse(null);
        return new SpiderResponse(
                spider.getId(),
                spider.getName(),
                spider.getTargetUrl(),
                spider.getStatus(),
                spider.getLastRunAt(),
                lastRun != null ? toRunResponse(lastRun) : null
        );
    }

    private SpiderRunResponse toRunResponse(SpiderRun run) {
        return new SpiderRunResponse(
                run.getId(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getQuestionsInserted(),
                run.getErrors()
        );
    }
}
