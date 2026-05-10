package com.versus.api.auth;

import com.versus.api.auth.domain.RefreshToken;
import com.versus.api.auth.dto.AuthResponse;
import com.versus.api.auth.dto.LoginRequest;
import com.versus.api.auth.dto.RegisterRequest;
import com.versus.api.auth.repo.RefreshTokenRepository;
import com.versus.api.common.exception.ApiException;
import com.versus.api.common.exception.ErrorCode;
import com.versus.api.users.Role;
import com.versus.api.users.UserStatus;
import com.versus.api.users.domain.User;
import com.versus.api.users.repo.UserRepository;
import io.jsonwebtoken.Claims;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("AuthService")
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository users;
    @Mock RefreshTokenRepository refreshTokens;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwt;

    @InjectMocks AuthService authService;

    // ═══════════════════════════════════════════════════════════════════════
    // REGISTER
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("Registro de usuario")
    @Nested
    class Register {

        private static final String USERNAME      = "player1";
        private static final String EMAIL         = "player1@versus.com";
        private static final String PASSWORD      = "Segura123!";
        private static final String HASHED_PW    = "$2a$10$mockedhash";
        private static final String ACCESS_TOKEN  = "mocked.access.token";
        private static final String REFRESH_TOKEN = "mocked.refresh.token";
        private static final String REFRESH_HASH  = "mockedrefreshhash";

        private RegisterRequest validRequest() {
            return new RegisterRequest(USERNAME, EMAIL, PASSWORD);
        }

        private void stubHappyPath() {
            when(users.existsByEmail(EMAIL)).thenReturn(false);
            when(users.existsByUsername(USERNAME)).thenReturn(false);
            when(passwordEncoder.encode(PASSWORD)).thenReturn(HASHED_PW);
            when(users.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(UUID.randomUUID());
                return u;
            });
            when(jwt.generateAccessToken(any())).thenReturn(ACCESS_TOKEN);
            when(jwt.generateRefreshToken(any())).thenReturn(REFRESH_TOKEN);
            when(jwt.hash(REFRESH_TOKEN)).thenReturn(REFRESH_HASH);
            when(jwt.getRefreshExpiry()).thenReturn(604800L);
        }

        @DisplayName("Camino feliz: registro exitoso con datos válidos")
        @Test
        void caminoFeliz_devuelveAuthResponseCompleta() {
            stubHappyPath();

            AuthResponse res = authService.register(validRequest());

            assertThat(res.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(res.refreshToken()).isEqualTo(REFRESH_TOKEN);
            assertThat(res.user().username()).isEqualTo(USERNAME);
            assertThat(res.user().role()).isEqualTo("PLAYER");
            assertThat(res.user().id()).isNotNull();
        }

        @DisplayName("Registro con email duplicado lanza conflicto")
        @Test
        void emailDuplicado_lanzaConflict() {
            when(users.existsByEmail(EMAIL)).thenReturn(true);

            assertThatThrownBy(() -> authService.register(validRequest()))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getCode())
                            .isEqualTo(ErrorCode.CONFLICT));
        }

        @DisplayName("Registro con username duplicado lanza conflicto")
        @Test
        void usernameDuplicado_lanzaConflict() {
            when(users.existsByEmail(EMAIL)).thenReturn(false);
            when(users.existsByUsername(USERNAME)).thenReturn(true);

            assertThatThrownBy(() -> authService.register(validRequest()))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getCode())
                            .isEqualTo(ErrorCode.CONFLICT));
        }

        @DisplayName("El refresh token se persiste con los campos correctos")
        @Test
        void caminoFeliz_persisteRefreshTokenConCamposCorrectos() {
            stubHappyPath();

            authService.register(validRequest());

            ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokens).save(captor.capture());

            RefreshToken saved = captor.getValue();
            assertThat(saved.getRevoked()).isFalse();
            assertThat(saved.getTokenHash()).isEqualTo(REFRESH_HASH);
            assertThat(saved.getUserId()).isNotNull();
            assertThat(saved.getExpiresAt()).isAfter(Instant.now());
        }

        @DisplayName("El usuario guardado tiene role PLAYER")
        @Test
        void usuarioGuardado_tieneRolePlayer() {
            stubHappyPath();

            authService.register(validRequest());

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(users).save(captor.capture());
            assertThat(captor.getValue().getRole()).isEqualTo(Role.PLAYER);
        }

        @DisplayName("El usuario guardado tiene isActive true")
        @Test
        void usuarioGuardado_tieneIsActiveTrue() {
            stubHappyPath();

            authService.register(validRequest());

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(users).save(captor.capture());
            assertThat(captor.getValue().getIsActive()).isTrue();
        }

        @DisplayName("La contraseña se guarda hasheada, no en plano")
        @Test
        void contrasena_seGuardaHasheadaNoEnPlano() {
            stubHappyPath();

            authService.register(validRequest());

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(users).save(captor.capture());
            assertThat(captor.getValue().getPasswordHash())
                    .isEqualTo(HASHED_PW)
                    .isNotEqualTo(PASSWORD);
        }

        @DisplayName("Usuario sin avatar tiene avatarUrl null sin lanzar excepción")
        @Test
        void usuarioSinAvatar_avatarUrlEsNullSinExcepcion() {
            stubHappyPath();

            AuthResponse res = authService.register(validRequest());

            assertThat(res.user().avatarUrl()).isNull();
        }

        @DisplayName("Si email y username ambos están duplicados, se lanza conflicto por email primero")
        @Test
        void emailYUsernameAmbosDuplicados_lanzaConflictPorEmailPrimero() {
            when(users.existsByEmail(EMAIL)).thenReturn(true);

            assertThatThrownBy(() -> authService.register(validRequest()))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getCode())
                            .isEqualTo(ErrorCode.CONFLICT));

            verify(users, never()).existsByUsername(any());
        }

        @DisplayName("Los tokens de acceso y refresh son distintos")
        @Test
        void tokens_accessYRefreshSonDistintos() {
            stubHappyPath();

            AuthResponse res = authService.register(validRequest());

            assertThat(res.accessToken()).isNotEqualTo(res.refreshToken());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LOGIN
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("Login de usuario")
    @Nested
    class Login {

        private static final String EMAIL         = "player1@versus.com";
        private static final String PASSWORD      = "Segura123!";
        private static final String HASHED_PW    = "$2a$10$mockedhash";
        private static final String ACCESS_TOKEN  = "mocked.access.token";
        private static final String REFRESH_TOKEN = "mocked.refresh.token";
        private static final String REFRESH_HASH  = "mockedrefreshhash";

        private LoginRequest validRequest() {
            return new LoginRequest(EMAIL, PASSWORD);
        }

        private User activeUser() {
            return User.builder()
                    .id(UUID.randomUUID())
                    .username("player1")
                    .email(EMAIL)
                    .passwordHash(HASHED_PW)
                    .role(Role.PLAYER)
                    .isActive(true)
                    .build();
        }

        private void stubHappyPath(User user) {
            when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, HASHED_PW)).thenReturn(true);
            when(jwt.generateAccessToken(any())).thenReturn(ACCESS_TOKEN);
            when(jwt.generateRefreshToken(any())).thenReturn(REFRESH_TOKEN);
            when(jwt.hash(REFRESH_TOKEN)).thenReturn(REFRESH_HASH);
            when(jwt.getRefreshExpiry()).thenReturn(604800L);
        }

        @DisplayName("Camino feliz: credenciales correctas devuelven AuthResponse")
        @Test
        void caminoFeliz_devuelveAuthResponseCompleta() {
            User user = activeUser();
            stubHappyPath(user);

            AuthResponse res = authService.login(validRequest());

            assertThat(res.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(res.refreshToken()).isEqualTo(REFRESH_TOKEN);
            assertThat(res.user().username()).isEqualTo("player1");
            assertThat(res.user().role()).isEqualTo("PLAYER");
        }

        @DisplayName("Email no registrado lanza UNAUTHORIZED")
        @Test
        void emailNoExiste_lanzaUnauthorized() {
            when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(validRequest()))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getCode())
                            .isEqualTo(ErrorCode.UNAUTHORIZED));
        }

        @DisplayName("Contraseña incorrecta lanza UNAUTHORIZED")
        @Test
        void contrasenaIncorrecta_lanzaUnauthorized() {
            when(users.findByEmail(EMAIL)).thenReturn(Optional.of(activeUser()));
            when(passwordEncoder.matches(PASSWORD, HASHED_PW)).thenReturn(false);

            assertThatThrownBy(() -> authService.login(validRequest()))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getCode())
                            .isEqualTo(ErrorCode.UNAUTHORIZED));
        }

        @DisplayName("Usuario inactivo lanza UNAUTHORIZED")
        @Test
        void usuarioInactivo_lanzaUnauthorized() {
            User inactive = User.builder()
                    .id(UUID.randomUUID()).email(EMAIL).passwordHash(HASHED_PW)
                    .role(Role.PLAYER).isActive(false).build();
            when(users.findByEmail(EMAIL)).thenReturn(Optional.of(inactive));

            assertThatThrownBy(() -> authService.login(validRequest()))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getCode())
                            .isEqualTo(ErrorCode.UNAUTHORIZED));
        }

        @DisplayName("Usuario inactivo: la contraseña no se verifica (orden de guardas)")
        @Test
        void usuarioInactivo_noVerificaContrasena() {
            User inactive = User.builder()
                    .id(UUID.randomUUID()).email(EMAIL).passwordHash(HASHED_PW)
                    .role(Role.PLAYER).isActive(false).build();
            when(users.findByEmail(EMAIL)).thenReturn(Optional.of(inactive));

            assertThatThrownBy(() -> authService.login(validRequest()))
                    .isInstanceOf(ApiException.class);

            verify(passwordEncoder, never()).matches(any(), any());
        }

        @DisplayName("Usuario con status DELETED lanza UNAUTHORIZED")
        @Test
        void usuarioDeleted_lanzaUnauthorized() {
            User deleted = activeUser();
            deleted.setStatus(UserStatus.DELETED);
            when(users.findByEmail(EMAIL)).thenReturn(Optional.of(deleted));

            assertThatThrownBy(() -> authService.login(validRequest()))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getCode())
                            .isEqualTo(ErrorCode.UNAUTHORIZED));

            verify(passwordEncoder, never()).matches(any(), any());
        }

        @DisplayName("Camino feliz: el refresh token se persiste con userId correcto")
        @Test
        void caminoFeliz_persisteRefreshTokenConUserId() {
            User user = activeUser();
            stubHappyPath(user);

            authService.login(validRequest());

            ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokens).save(captor.capture());
            assertThat(captor.getValue().getUserId()).isEqualTo(user.getId());
            assertThat(captor.getValue().getRevoked()).isFalse();
            assertThat(captor.getValue().getExpiresAt()).isAfter(Instant.now());
        }

        @DisplayName("El mensaje de error es igual para email inexistente y contraseña incorrecta")
        @Test
        void mensajeError_emailYContrasena_sonIguales() {
            when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());
            ApiException noEmail = catchThrowableOfType(
                    () -> authService.login(validRequest()), ApiException.class);

            when(users.findByEmail(EMAIL)).thenReturn(Optional.of(activeUser()));
            when(passwordEncoder.matches(PASSWORD, HASHED_PW)).thenReturn(false);
            ApiException wrongPw = catchThrowableOfType(
                    () -> authService.login(validRequest()), ApiException.class);

            assertThat(noEmail.getMessage()).isEqualTo(wrongPw.getMessage());
        }

        @DisplayName("isActive=null no bloquea el login (Boolean.FALSE.equals(null) es false)")
        @Test
        void isActiveNull_noBloqueoLogin() {
            User nullActive = User.builder()
                    .id(UUID.randomUUID()).email(EMAIL).passwordHash(HASHED_PW)
                    .role(Role.PLAYER).isActive(null).build();
            stubHappyPath(nullActive);

            assertThatCode(() -> authService.login(validRequest())).doesNotThrowAnyException();
        }

        @DisplayName("Camino feliz: accessToken y refreshToken son distintos")
        @Test
        void caminoFeliz_accessYRefreshSonDistintos() {
            stubHappyPath(activeUser());

            AuthResponse res = authService.login(validRequest());

            assertThat(res.accessToken()).isNotEqualTo(res.refreshToken());
        }

        @DisplayName("Camino feliz: la respuesta contiene el id del usuario")
        @Test
        void caminoFeliz_respuestaContieneIdDeUsuario() {
            User user = activeUser();
            stubHappyPath(user);

            AuthResponse res = authService.login(validRequest());

            assertThat(res.user().id()).isEqualTo(user.getId().toString());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // REFRESH
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("Refresco de token")
    @Nested
    class Refresh {

        private static final String RAW_TOKEN       = "raw.refresh.token";
        private static final String TOKEN_HASH      = "hashedtoken123";
        private static final String NEW_ACCESS      = "new.access.token";
        private static final String NEW_REFRESH     = "new.refresh.token";
        private static final String NEW_REFRESH_HASH = "newhashedrefresh";
        private static final UUID   USER_ID         = UUID.randomUUID();

        private RefreshToken validStoredToken() {
            return RefreshToken.builder()
                    .id(UUID.randomUUID())
                    .userId(USER_ID)
                    .tokenHash(TOKEN_HASH)
                    .revoked(false)
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
        }

        private User storedUser() {
            return User.builder()
                    .id(USER_ID).username("player1").email("player1@versus.com")
                    .passwordHash("$2a$hash").role(Role.PLAYER).isActive(true)
                    .build();
        }

        private void stubHappyPath(RefreshToken token, User user) {
            Claims claims = mock(Claims.class);
            when(jwt.validate(RAW_TOKEN)).thenReturn(true);
            when(jwt.parse(RAW_TOKEN)).thenReturn(claims);
            when(claims.get("type", String.class)).thenReturn("refresh");
            when(jwt.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
            when(refreshTokens.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(token));
            when(users.findById(USER_ID)).thenReturn(Optional.of(user));
            when(jwt.generateAccessToken(any())).thenReturn(NEW_ACCESS);
            when(jwt.generateRefreshToken(any())).thenReturn(NEW_REFRESH);
            when(jwt.hash(NEW_REFRESH)).thenReturn(NEW_REFRESH_HASH);
            when(jwt.getRefreshExpiry()).thenReturn(604800L);
        }

        @DisplayName("Camino feliz: devuelve nuevos tokens")
        @Test
        void caminoFeliz_devuelveNuevosTokens() {
            stubHappyPath(validStoredToken(), storedUser());

            AuthResponse res = authService.refresh(RAW_TOKEN);

            assertThat(res.accessToken()).isEqualTo(NEW_ACCESS);
            assertThat(res.refreshToken()).isEqualTo(NEW_REFRESH);
        }

        @DisplayName("Token JWT malformado o inválido lanza UNAUTHORIZED")
        @Test
        void tokenJwtInvalido_lanzaUnauthorized() {
            when(jwt.validate(RAW_TOKEN)).thenReturn(false);

            assertThatThrownBy(() -> authService.refresh(RAW_TOKEN))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getCode())
                            .isEqualTo(ErrorCode.UNAUTHORIZED));
        }

        @DisplayName("Usar un accessToken como refreshToken lanza UNAUTHORIZED")
        @Test
        void tokenDeAccesoUsadoComoRefresh_lanzaUnauthorized() {
            Claims claims = mock(Claims.class);
            when(jwt.validate(RAW_TOKEN)).thenReturn(true);
            when(jwt.parse(RAW_TOKEN)).thenReturn(claims);
            when(claims.get("type", String.class)).thenReturn("access");

            assertThatThrownBy(() -> authService.refresh(RAW_TOKEN))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getCode())
                            .isEqualTo(ErrorCode.UNAUTHORIZED));
        }

        @DisplayName("Token no encontrado en BD lanza UNAUTHORIZED")
        @Test
        void tokenNoEncontradoEnBD_lanzaUnauthorized() {
            Claims claims = mock(Claims.class);
            when(jwt.validate(RAW_TOKEN)).thenReturn(true);
            when(jwt.parse(RAW_TOKEN)).thenReturn(claims);
            when(claims.get("type", String.class)).thenReturn("refresh");
            when(jwt.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
            when(refreshTokens.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refresh(RAW_TOKEN))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getCode())
                            .isEqualTo(ErrorCode.UNAUTHORIZED));
        }

        @DisplayName("Token ya revocado lanza UNAUTHORIZED")
        @Test
        void tokenRevocado_lanzaUnauthorized() {
            RefreshToken revoked = validStoredToken();
            revoked.setRevoked(true);
            Claims claims = mock(Claims.class);
            when(jwt.validate(RAW_TOKEN)).thenReturn(true);
            when(jwt.parse(RAW_TOKEN)).thenReturn(claims);
            when(claims.get("type", String.class)).thenReturn("refresh");
            when(jwt.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
            when(refreshTokens.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(revoked));

            assertThatThrownBy(() -> authService.refresh(RAW_TOKEN))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getCode())
                            .isEqualTo(ErrorCode.UNAUTHORIZED));
        }

        @DisplayName("Token expirado lanza UNAUTHORIZED")
        @Test
        void tokenExpirado_lanzaUnauthorized() {
            RefreshToken expired = RefreshToken.builder()
                    .id(UUID.randomUUID()).userId(USER_ID).tokenHash(TOKEN_HASH)
                    .revoked(false).expiresAt(Instant.now().minusSeconds(1)).build();
            Claims claims = mock(Claims.class);
            when(jwt.validate(RAW_TOKEN)).thenReturn(true);
            when(jwt.parse(RAW_TOKEN)).thenReturn(claims);
            when(claims.get("type", String.class)).thenReturn("refresh");
            when(jwt.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
            when(refreshTokens.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(expired));

            assertThatThrownBy(() -> authService.refresh(RAW_TOKEN))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getCode())
                            .isEqualTo(ErrorCode.UNAUTHORIZED));
        }

        @DisplayName("Camino feliz: el token anterior queda revocado (rotación)")
        @Test
        void caminoFeliz_tokenAnteriorQuedaRevocado() {
            RefreshToken token = validStoredToken();
            stubHappyPath(token, storedUser());

            authService.refresh(RAW_TOKEN);

            assertThat(token.getRevoked()).isTrue();
            verify(refreshTokens, atLeastOnce()).save(token);
        }

        @DisplayName("UserId del token no existe en BD lanza UNAUTHORIZED")
        @Test
        void usuarioNoEncontradoEnBD_lanzaUnauthorized() {
            Claims claims = mock(Claims.class);
            when(jwt.validate(RAW_TOKEN)).thenReturn(true);
            when(jwt.parse(RAW_TOKEN)).thenReturn(claims);
            when(claims.get("type", String.class)).thenReturn("refresh");
            when(jwt.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
            when(refreshTokens.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(validStoredToken()));
            when(users.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refresh(RAW_TOKEN))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getCode())
                            .isEqualTo(ErrorCode.UNAUTHORIZED));
        }

        @DisplayName("Camino feliz: el nuevo refreshToken es distinto al original")
        @Test
        void caminoFeliz_nuevoRefreshTokenDistintoAlOriginal() {
            stubHappyPath(validStoredToken(), storedUser());

            AuthResponse res = authService.refresh(RAW_TOKEN);

            assertThat(res.refreshToken()).isNotEqualTo(RAW_TOKEN);
            assertThat(res.accessToken()).isNotEqualTo(RAW_TOKEN);
        }

        @DisplayName("Camino feliz: se persiste el nuevo refresh token con revoked=false")
        @Test
        void caminoFeliz_persisteNuevoRefreshToken() {
            stubHappyPath(validStoredToken(), storedUser());

            authService.refresh(RAW_TOKEN);

            ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokens, times(2)).save(captor.capture());
            assertThat(captor.getAllValues())
                    .anySatisfy(t -> assertThat(t.getRevoked()).isFalse());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LOGOUT
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("Logout de usuario")
    @Nested
    class Logout {

        private static final String RAW_TOKEN  = "raw.logout.token";
        private static final String TOKEN_HASH = "logouthash123";

        private RefreshToken activeToken() {
            return RefreshToken.builder()
                    .id(UUID.randomUUID()).userId(UUID.randomUUID())
                    .tokenHash(TOKEN_HASH).revoked(false)
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
        }

        @DisplayName("Camino feliz: el token es revocado y guardado")
        @Test
        void caminoFeliz_tokenEsRevocadoYGuardado() {
            RefreshToken token = activeToken();
            when(jwt.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
            when(refreshTokens.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(token));

            authService.logout(RAW_TOKEN);

            assertThat(token.getRevoked()).isTrue();
            verify(refreshTokens).save(token);
        }

        @DisplayName("Token null retorna silenciosamente sin acceder a BD")
        @Test
        void tokenNull_retornaSinInteractuarConBD() {
            authService.logout(null);

            verifyNoInteractions(jwt, refreshTokens);
        }

        @DisplayName("Token blank (espacios) retorna silenciosamente sin acceder a BD")
        @Test
        void tokenBlank_retornaSinInteractuarConBD() {
            authService.logout("   ");

            verifyNoInteractions(jwt, refreshTokens);
        }

        @DisplayName("Token vacío retorna silenciosamente sin acceder a BD")
        @Test
        void tokenVacio_retornaSinInteractuarConBD() {
            authService.logout("");

            verifyNoInteractions(jwt, refreshTokens);
        }

        @DisplayName("Token no encontrado en BD no lanza excepción ni guarda")
        @Test
        void tokenNoEncontradoEnBD_noLanzaExcepcionNiGuarda() {
            when(jwt.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
            when(refreshTokens.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.empty());

            assertThatCode(() -> authService.logout(RAW_TOKEN)).doesNotThrowAnyException();
            verify(refreshTokens, never()).save(any());
        }

        @DisplayName("Token ya revocado no lanza excepción (idempotente)")
        @Test
        void tokenYaRevocado_noLanzaExcepcion() {
            RefreshToken already = RefreshToken.builder()
                    .id(UUID.randomUUID()).userId(UUID.randomUUID())
                    .tokenHash(TOKEN_HASH).revoked(true)
                    .expiresAt(Instant.now().plusSeconds(3600)).build();
            when(jwt.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
            when(refreshTokens.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(already));

            assertThatCode(() -> authService.logout(RAW_TOKEN)).doesNotThrowAnyException();
            verify(refreshTokens).save(already);
        }

        @DisplayName("Camino feliz: se usa el hash del token para buscar en BD")
        @Test
        void caminoFeliz_hashDelTokenEsUsadoParaBuscar() {
            when(jwt.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
            when(refreshTokens.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.empty());

            authService.logout(RAW_TOKEN);

            verify(jwt).hash(RAW_TOKEN);
            verify(refreshTokens).findByTokenHash(TOKEN_HASH);
        }

        @DisplayName("Camino feliz: el campo revoked queda en true tras el logout")
        @Test
        void caminoFeliz_revokedSetToTrue() {
            RefreshToken token = activeToken();
            when(jwt.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
            when(refreshTokens.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(token));

            authService.logout(RAW_TOKEN);

            ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokens).save(captor.capture());
            assertThat(captor.getValue().getRevoked()).isTrue();
        }

        @DisplayName("Camino feliz: save es llamado exactamente una vez")
        @Test
        void caminoFeliz_saveEsLlamadoExactamenteUnaVez() {
            when(jwt.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
            when(refreshTokens.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(activeToken()));

            authService.logout(RAW_TOKEN);

            verify(refreshTokens, times(1)).save(any());
        }

        @DisplayName("Doble logout con el mismo token es idempotente")
        @Test
        void dobleLogout_esIdempotente() {
            RefreshToken token = activeToken();
            when(jwt.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
            when(refreshTokens.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(token));

            authService.logout(RAW_TOKEN);
            assertThatCode(() -> authService.logout(RAW_TOKEN)).doesNotThrowAnyException();
        }
    }
}
