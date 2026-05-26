package com.versus.api.admin;

import com.versus.api.admin.dto.*;
import com.versus.api.common.exception.ApiException;
import com.versus.api.match.repo.MatchRepository;
import com.versus.api.moderation.ReportStatus;
import com.versus.api.moderation.domain.QuestionReport;
import com.versus.api.moderation.repo.QuestionReportRepository;
import com.versus.api.questions.repo.QuestionRepository;
import com.versus.api.scraping.SpiderStatus;
import com.versus.api.scraping.domain.SpiderRun;
import com.versus.api.scraping.repo.SpiderRepository;
import com.versus.api.scraping.repo.SpiderRunRepository;
import com.versus.api.users.Role;
import com.versus.api.users.domain.User;
import com.versus.api.users.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository users;
    private final MatchRepository matches;
    private final QuestionRepository questions;
    private final SpiderRepository spiders;
    private final SpiderRunRepository spiderRuns;
    private final QuestionReportRepository reports;

    @Transactional(readOnly = true)
    public AdminUserPageResponse getUsers(int page, int size, String search, Role role, Boolean active) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        String searchParam = (search != null && !search.isBlank()) ? search : null;
        Page<User> result = users.searchUsers(searchParam, role, active, pageable);
        List<AdminUserResponse> items = result.getContent().stream().map(this::toDto).toList();
        return new AdminUserPageResponse(items, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public AdminUserResponse updateRole(UUID adminId, UUID targetId, Role newRole) {
        if (adminId.equals(targetId)) {
            throw ApiException.validation("Cannot change your own role");
        }
        User user = users.findById(targetId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        user.setRole(newRole);
        return toDto(users.save(user));
    }

    @Transactional
    public AdminUserResponse updateStatus(UUID adminId, UUID targetId, boolean active) {
        if (adminId.equals(targetId)) {
            throw ApiException.validation("Cannot change your own status");
        }
        User user = users.findById(targetId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        user.setIsActive(active);
        return toDto(users.save(user));
    }

    @Transactional(readOnly = true)
    public AdminStatsResponse getStats() {
        Instant startOfToday = Instant.now().truncatedTo(ChronoUnit.DAYS);
        return new AdminStatsResponse(
                users.count(),
                users.countByIsActive(true),
                matches.countByCreatedAtAfter(startOfToday),
                questions.count(),
                spiders.countByStatus(SpiderStatus.RUNNING),
                reports.countByStatus(ReportStatus.PENDING)
        );
    }

    @Transactional(readOnly = true)
    public List<AdminLogResponse> getLogs(int limit) {
        int perSource = Math.max(limit, 10);

        List<AdminLogResponse> entries = new ArrayList<>();

        spiderRuns.findAll(PageRequest.of(0, perSource, Sort.by(Sort.Direction.DESC, "startedAt")))
                .forEach(run -> entries.add(toLogEntry(run)));

        users.findAll(PageRequest.of(0, perSource, Sort.by(Sort.Direction.DESC, "createdAt")))
                .forEach(u -> entries.add(new AdminLogResponse(
                        u.getCreatedAt(), "INFO", "New user registered: " + u.getUsername())));

        reports.findAll(PageRequest.of(0, perSource, Sort.by(Sort.Direction.DESC, "createdAt")))
                .forEach(r -> entries.add(toLogEntry(r)));

        return entries.stream()
                .sorted(Comparator.comparing(AdminLogResponse::ts).reversed())
                .limit(limit)
                .toList();
    }

    private AdminLogResponse toLogEntry(SpiderRun run) {
        int errors = run.getErrors() != null ? run.getErrors() : 0;
        int inserted = run.getQuestionsInserted() != null ? run.getQuestionsInserted() : 0;
        String level = errors == 0 ? "INFO" : (errors < 3 ? "WARN" : "ERR");
        String msg = errors == 0
                ? "Spider run completed: " + inserted + " questions inserted"
                : "Spider run finished with " + errors + " errors, " + inserted + " questions inserted";
        return new AdminLogResponse(run.getStartedAt(), level, msg);
    }

    private AdminLogResponse toLogEntry(QuestionReport report) {
        String reason = report.getReason() != null ? report.getReason().name() : "no reason given";
        return new AdminLogResponse(report.getCreatedAt(), "INFO",
                "Question report submitted: " + reason);
    }

    private AdminUserResponse toDto(User u) {
        return new AdminUserResponse(
                u.getId().toString(),
                u.getUsername(),
                u.getEmail(),
                u.getRole().name(),
                u.getIsActive(),
                u.getCreatedAt()
        );
    }
}
