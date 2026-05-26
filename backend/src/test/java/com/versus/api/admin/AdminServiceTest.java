package com.versus.api.admin;

import com.versus.api.admin.dto.AdminLogResponse;
import com.versus.api.admin.dto.AdminStatsResponse;
import com.versus.api.admin.dto.AdminUserPageResponse;
import com.versus.api.admin.dto.AdminUserResponse;
import com.versus.api.common.exception.ApiException;
import com.versus.api.common.exception.ErrorCode;
import com.versus.api.match.repo.MatchRepository;
import com.versus.api.moderation.ReportReason;
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
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("AdminService")
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock UserRepository users;
    @Mock MatchRepository matches;
    @Mock QuestionRepository questions;
    @Mock SpiderRepository spiders;
    @Mock SpiderRunRepository spiderRuns;
    @Mock QuestionReportRepository reports;

    @InjectMocks AdminService adminService;

    static final UUID ADMIN_ID  = UUID.fromString("aaaa0000-0000-0000-0000-000000000001");
    static final UUID TARGET_ID = UUID.fromString("bbbb0000-0000-0000-0000-000000000002");

    private User user(UUID id, String username, Role role) {
        return User.builder()
                .id(id).username(username).email(username + "@test.com")
                .passwordHash("x").role(role)
                .isActive(true).createdAt(Instant.now())
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // getUsers
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("getUsers")
    @Nested
    class GetUsers {

        @DisplayName("Búsqueda en blanco normaliza a null en el repositorio")
        @Test
        void busquedaEnBlanco_pasaNullAlRepositorio() {
            when(users.searchUsers(any(), any(), any(), any())).thenReturn(Page.empty());
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

            adminService.getUsers(0, 20, "   ", null, null);

            verify(users).searchUsers(captor.capture(), any(), any(), any());
            assertThat(captor.getValue()).isNull();
        }

        @DisplayName("Resultado paginado se mapea correctamente al DTO")
        @Test
        void resultadoPaginado_seMapeaCorrectamente() {
            User u = user(TARGET_ID, "alice", Role.PLAYER);
            Page<User> page = new PageImpl<>(List.of(u));
            when(users.searchUsers(any(), any(), any(), any())).thenReturn(page);

            AdminUserPageResponse response = adminService.getUsers(0, 20, null, null, null);

            assertThat(response.totalElements()).isEqualTo(1);
            assertThat(response.items()).hasSize(1);
            assertThat(response.items().get(0).id()).isEqualTo(TARGET_ID.toString());
            assertThat(response.items().get(0).role()).isEqualTo("PLAYER");
        }

        @DisplayName("Con filtros, se pasan al repositorio correctamente")
        @Test
        void conFiltros_sePassanAlRepositorio() {
            when(users.searchUsers(any(), any(), any(), any())).thenReturn(Page.empty());
            ArgumentCaptor<String> searchCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Role> roleCaptor     = ArgumentCaptor.forClass(Role.class);
            ArgumentCaptor<Boolean> activeCaptor = ArgumentCaptor.forClass(Boolean.class);

            adminService.getUsers(0, 10, "alice", Role.ADMIN, true);

            verify(users).searchUsers(searchCaptor.capture(), roleCaptor.capture(),
                    activeCaptor.capture(), any());
            assertThat(searchCaptor.getValue()).isEqualTo("alice");
            assertThat(roleCaptor.getValue()).isEqualTo(Role.ADMIN);
            assertThat(activeCaptor.getValue()).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // updateRole
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("updateRole")
    @Nested
    class UpdateRole {

        @DisplayName("Admin que cambia su propio rol lanza VALIDATION_ERROR")
        @Test
        void propioAdmin_lanzaValidationException() {
            assertThatThrownBy(() -> adminService.updateRole(ADMIN_ID, ADMIN_ID, Role.PLAYER))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode())
                            .isEqualTo(ErrorCode.VALIDATION_ERROR));
            verify(users, never()).findById(any());
        }

        @DisplayName("Usuario no encontrado lanza NOT_FOUND")
        @Test
        void usuarioNoEncontrado_lanzaNotFoundException() {
            when(users.findById(TARGET_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.updateRole(ADMIN_ID, TARGET_ID, Role.MODERATOR))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode())
                            .isEqualTo(ErrorCode.NOT_FOUND));
        }

        @DisplayName("Rol actualizado: guarda el nuevo rol en el repositorio")
        @Test
        void rolActualizado_guardaElNuevoRol() {
            User existing = user(TARGET_ID, "alice", Role.PLAYER);
            when(users.findById(TARGET_ID)).thenReturn(Optional.of(existing));
            when(users.save(any(User.class))).thenReturn(existing);
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);

            adminService.updateRole(ADMIN_ID, TARGET_ID, Role.MODERATOR);

            verify(users).save(captor.capture());
            assertThat(captor.getValue().getRole()).isEqualTo(Role.MODERATOR);
        }

        @DisplayName("Devuelve AdminUserResponse con los datos correctos")
        @Test
        void devuelveAdminUserResponse() {
            User existing = user(TARGET_ID, "alice", Role.MODERATOR);
            existing.setAvatarUrl("data:image/png;base64,abc");
            when(users.findById(TARGET_ID)).thenReturn(Optional.of(existing));
            when(users.save(any(User.class))).thenReturn(existing);

            AdminUserResponse response = adminService.updateRole(ADMIN_ID, TARGET_ID, Role.MODERATOR);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(TARGET_ID.toString());
            assertThat(response.avatarUrl()).isEqualTo("data:image/png;base64,abc");
            assertThat(response.role()).isEqualTo("MODERATOR");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // updateStatus
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("updateStatus")
    @Nested
    class UpdateStatus {

        @DisplayName("Admin que suspende su propia cuenta lanza VALIDATION_ERROR")
        @Test
        void propioAdmin_lanzaValidationException() {
            assertThatThrownBy(() -> adminService.updateStatus(ADMIN_ID, ADMIN_ID, false))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode())
                            .isEqualTo(ErrorCode.VALIDATION_ERROR));
            verify(users, never()).findById(any());
        }

        @DisplayName("Usuario no encontrado lanza NOT_FOUND")
        @Test
        void usuarioNoEncontrado_lanzaNotFoundException() {
            when(users.findById(TARGET_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.updateStatus(ADMIN_ID, TARGET_ID, false))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode())
                            .isEqualTo(ErrorCode.NOT_FOUND));
        }

        @DisplayName("Cuenta desactivada: guarda isActive=false")
        @Test
        void cuentaDesactivada_isActiveEsFalse() {
            User existing = user(TARGET_ID, "alice", Role.PLAYER);
            when(users.findById(TARGET_ID)).thenReturn(Optional.of(existing));
            when(users.save(any(User.class))).thenReturn(existing);
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);

            adminService.updateStatus(ADMIN_ID, TARGET_ID, false);

            verify(users).save(captor.capture());
            assertThat(captor.getValue().getIsActive()).isFalse();
        }

        @DisplayName("Cuenta reactivada: guarda isActive=true")
        @Test
        void cuentaReactivada_isActiveEsTrue() {
            User existing = user(TARGET_ID, "alice", Role.PLAYER);
            existing.setIsActive(false);
            when(users.findById(TARGET_ID)).thenReturn(Optional.of(existing));
            when(users.save(any(User.class))).thenReturn(existing);
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);

            adminService.updateStatus(ADMIN_ID, TARGET_ID, true);

            verify(users).save(captor.capture());
            assertThat(captor.getValue().getIsActive()).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // getStats
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("getStats")
    @Nested
    class GetStats {

        @DisplayName("Devuelve los conteos correctos de cada repositorio")
        @Test
        void devuelveConteosCorrrectos() {
            when(users.count()).thenReturn(100L);
            when(users.countByIsActive(true)).thenReturn(80L);
            when(matches.countByCreatedAtAfter(any())).thenReturn(5L);
            when(questions.count()).thenReturn(200L);
            when(spiders.countByStatus(SpiderStatus.RUNNING)).thenReturn(2L);
            when(reports.countByStatus(ReportStatus.PENDING)).thenReturn(3L);

            AdminStatsResponse stats = adminService.getStats();

            assertThat(stats.totalUsers()).isEqualTo(100L);
            assertThat(stats.activeUsers()).isEqualTo(80L);
            assertThat(stats.matchesToday()).isEqualTo(5L);
            assertThat(stats.totalQuestions()).isEqualTo(200L);
            assertThat(stats.activeSpiders()).isEqualTo(2L);
            assertThat(stats.pendingReports()).isEqualTo(3L);
        }

        @DisplayName("matchesToday usa el inicio del día UTC actual")
        @Test
        void matchesToday_usaStartOfTodayUTC() {
            when(users.count()).thenReturn(0L);
            when(users.countByIsActive(anyBoolean())).thenReturn(0L);
            when(matches.countByCreatedAtAfter(any())).thenReturn(0L);
            when(questions.count()).thenReturn(0L);
            when(spiders.countByStatus(any())).thenReturn(0L);
            when(reports.countByStatus(any())).thenReturn(0L);
            ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);

            adminService.getStats();

            verify(matches).countByCreatedAtAfter(captor.capture());
            Instant expected = Instant.now().truncatedTo(ChronoUnit.DAYS);
            assertThat(captor.getValue()).isEqualTo(expected);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // getLogs
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("getLogs")
    @Nested
    class GetLogs {

        @DisplayName("Agrega entradas de tres fuentes y ordena por timestamp descendente")
        @Test
        void agregaEntresTresFuentes_yOrdenaPorTs() {
            Instant oldest  = Instant.now().minusSeconds(3600);
            Instant middle  = Instant.now().minusSeconds(1800);
            Instant newest  = Instant.now().minusSeconds(600);

            SpiderRun run = SpiderRun.builder().id(UUID.randomUUID()).spiderId(UUID.randomUUID())
                    .startedAt(oldest).questionsInserted(10).errors(0).build();
            User u = user(UUID.randomUUID(), "newuser", Role.PLAYER);
            u.setCreatedAt(middle);
            QuestionReport report = QuestionReport.builder().id(UUID.randomUUID())
                    .questionId(UUID.randomUUID()).reportedBy(UUID.randomUUID())
                    .reason(ReportReason.OTHER).status(ReportStatus.PENDING).createdAt(newest).build();

            when(spiderRuns.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(run)));
            when(users.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(u)));
            when(reports.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(report)));

            List<AdminLogResponse> logs = adminService.getLogs(10);

            assertThat(logs).hasSize(3);
            assertThat(logs.get(0).ts()).isAfterOrEqualTo(logs.get(1).ts());
            assertThat(logs.get(1).ts()).isAfterOrEqualTo(logs.get(2).ts());
        }

        @DisplayName("SpiderRun sin errores tiene nivel INFO")
        @Test
        void spiderRunSinErrores_nivelInfo() {
            SpiderRun run = SpiderRun.builder().id(UUID.randomUUID()).spiderId(UUID.randomUUID())
                    .startedAt(Instant.now()).questionsInserted(5).errors(0).build();

            when(spiderRuns.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(run)));
            when(users.findAll(any(Pageable.class))).thenReturn(Page.empty());
            when(reports.findAll(any(Pageable.class))).thenReturn(Page.empty());

            List<AdminLogResponse> logs = adminService.getLogs(10);

            assertThat(logs).hasSize(1);
            assertThat(logs.get(0).level()).isEqualTo("INFO");
        }

        @DisplayName("SpiderRun con 3 o más errores tiene nivel ERR")
        @Test
        void spiderRunConMuchosErrores_nivelErr() {
            SpiderRun run = SpiderRun.builder().id(UUID.randomUUID()).spiderId(UUID.randomUUID())
                    .startedAt(Instant.now()).questionsInserted(2).errors(3).build();

            when(spiderRuns.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(run)));
            when(users.findAll(any(Pageable.class))).thenReturn(Page.empty());
            when(reports.findAll(any(Pageable.class))).thenReturn(Page.empty());

            List<AdminLogResponse> logs = adminService.getLogs(10);

            assertThat(logs).hasSize(1);
            assertThat(logs.get(0).level()).isEqualTo("ERR");
        }
    }
}
