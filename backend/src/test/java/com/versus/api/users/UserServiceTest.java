package com.versus.api.users;

import com.versus.api.common.exception.ApiException;
import com.versus.api.common.exception.ErrorCode;
import com.versus.api.media.MediaService;
import com.versus.api.media.MediaKind;
import com.versus.api.media.MediaVisibility;
import com.versus.api.media.dto.MediaAssetResponse;
import com.versus.api.users.domain.User;
import com.versus.api.users.dto.ChangePasswordRequest;
import com.versus.api.users.dto.UpdateMeRequest;
import com.versus.api.users.dto.UserMeResponse;
import com.versus.api.users.repo.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("UserService")
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository users;
    @Mock PasswordEncoder passwordEncoder;
    @Mock MediaService mediaService;
    @InjectMocks UserService userService;

    private static final UUID USER_ID = UUID.fromString("bbbb0000-0000-0000-0000-000000000001");

    private User activeUser() {
        return User.builder()
                .id(USER_ID)
                .username("player")
                .email("player@versus.com")
                .passwordHash("$2a$hash")
                .avatarUrl(null)
                .role(Role.PLAYER)
                .status(UserStatus.ACTIVE)
                .isActive(true)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }

    private void stubActiveUser(User user) {
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    @DisplayName("Perfil")
    @Nested
    class Profile {

        @DisplayName("getMe devuelve el perfil autenticado activo")
        @Test
        void getMe_devuelvePerfilActivo() {
            stubActiveUser(activeUser());

            UserMeResponse res = userService.getMe(USER_ID);

            assertThat(res.id()).isEqualTo(USER_ID.toString());
            assertThat(res.username()).isEqualTo("player");
            assertThat(res.email()).isEqualTo("player@versus.com");
        }

        @DisplayName("Usuario DELETED se trata como no encontrado")
        @Test
        void usuarioDeleted_lanzaNotFound() {
            User user = activeUser();
            user.setStatus(UserStatus.DELETED);
            stubActiveUser(user);

            assertThatThrownBy(() -> userService.getMe(USER_ID))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getCode())
                            .isEqualTo(ErrorCode.NOT_FOUND));
        }

        @DisplayName("Usuario inactivo se trata como no encontrado")
        @Test
        void usuarioInactivo_lanzaNotFound() {
            User user = activeUser();
            user.setIsActive(false);
            stubActiveUser(user);

            assertThatThrownBy(() -> userService.getMe(USER_ID))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getCode())
                            .isEqualTo(ErrorCode.NOT_FOUND));
        }

        @DisplayName("updateMe cambia username si no esta en uso")
        @Test
        void updateMe_cambiaUsername() {
            User user = activeUser();
            stubActiveUser(user);
            when(users.existsByUsername("newplayer")).thenReturn(false);

            UserMeResponse res = userService.updateMe(USER_ID, new UpdateMeRequest("newplayer", null));

            assertThat(res.username()).isEqualTo("newplayer");
            verify(users).save(user);
        }

        @DisplayName("updateMe con username duplicado lanza CONFLICT")
        @Test
        void updateMe_usernameDuplicado_lanzaConflict() {
            stubActiveUser(activeUser());
            when(users.existsByUsername("taken")).thenReturn(true);

            assertThatThrownBy(() -> userService.updateMe(USER_ID, new UpdateMeRequest("taken", null)))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getCode())
                            .isEqualTo(ErrorCode.CONFLICT));
        }
    }

    @DisplayName("Password")
    @Nested
    class Password {

        @DisplayName("Cambiar password requiere password actual correcto")
        @Test
        void passwordActualIncorrecta_lanzaUnauthorized() {
            stubActiveUser(activeUser());
            when(passwordEncoder.matches("bad", "$2a$hash")).thenReturn(false);

            assertThatThrownBy(() -> userService.changePassword(
                    USER_ID, new ChangePasswordRequest("bad", "newpass123")))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getCode())
                            .isEqualTo(ErrorCode.UNAUTHORIZED));

            verify(users, never()).save(any());
        }

        @DisplayName("Camino feliz: guarda la nueva password hasheada")
        @Test
        void caminoFeliz_guardaNuevaPasswordHasheada() {
            User user = activeUser();
            stubActiveUser(user);
            when(passwordEncoder.matches("oldpass", "$2a$hash")).thenReturn(true);
            when(passwordEncoder.encode("newpass123")).thenReturn("$2a$newhash");

            userService.changePassword(USER_ID, new ChangePasswordRequest("oldpass", "newpass123"));

            assertThat(user.getPasswordHash()).isEqualTo("$2a$newhash");
            verify(users).save(user);
        }
    }

    @DisplayName("Avatar")
    @Nested
    class Avatar {

        @DisplayName("Seleccionar avatar predefinido guarda la URL")
        @Test
        void avatarPredefinido_guardaUrl() {
            User user = activeUser();
            stubActiveUser(user);

            UserMeResponse res = userService.updateAvatar(USER_ID, "https://avatar.test/a.svg");

            assertThat(res.avatarUrl()).isEqualTo("https://avatar.test/a.svg");
            assertThat(user.getAvatarUrl()).isEqualTo("https://avatar.test/a.svg");
            verify(users).save(user);
        }

        @DisplayName("Avatar predefinido vacio lanza VALIDATION_ERROR")
        @Test
        void avatarVacio_lanzaValidation() {
            stubActiveUser(activeUser());

            assertThatThrownBy(() -> userService.updateAvatar(USER_ID, " "))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getCode())
                            .isEqualTo(ErrorCode.VALIDATION_ERROR));
        }

        @DisplayName("Upload PNG guarda URL del storage")
        @Test
        void uploadPng_guardaUrlDelStorage() {
            User user = activeUser();
            stubActiveUser(user);
            MultipartFile file = mock(MultipartFile.class);
            MediaAssetResponse avatarResponse = new MediaAssetResponse(
                    "asset-id", MediaKind.IMAGE, "avatar.png", "image/png", 3,
                    MediaVisibility.PUBLIC, "https://storage/avatar.png", Instant.now());
            when(mediaService.uploadAvatar(USER_ID, file)).thenReturn(avatarResponse);

            UserMeResponse res = userService.updateAvatar(USER_ID, file);

            assertThat(res.avatarUrl()).isEqualTo("https://storage/avatar.png");
            verify(users).save(user);
        }

        @DisplayName("Upload mayor de 2MB lanza VALIDATION_ERROR")
        @Test
        void uploadMayorDe2Mb_lanzaValidation() {
            stubActiveUser(activeUser());
            MultipartFile file = mock(MultipartFile.class);
            when(mediaService.uploadAvatar(USER_ID, file))
                    .thenThrow(ApiException.validation("File exceeds maximum size"));

            assertThatThrownBy(() -> userService.updateAvatar(USER_ID, file))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getCode())
                            .isEqualTo(ErrorCode.VALIDATION_ERROR));
        }

        @DisplayName("Upload que no es imagen lanza VALIDATION_ERROR")
        @Test
        void uploadNoImagen_lanzaValidation() {
            stubActiveUser(activeUser());
            MultipartFile file = mock(MultipartFile.class);
            when(mediaService.uploadAvatar(USER_ID, file))
                    .thenThrow(ApiException.validation("Avatar must be an image"));

            assertThatThrownBy(() -> userService.updateAvatar(USER_ID, file))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getCode())
                            .isEqualTo(ErrorCode.VALIDATION_ERROR));
        }
    }

    @DisplayName("Delete")
    @Nested
    class Delete {

        @DisplayName("deleteMe anonimiza datos y marca status DELETED")
        @Test
        void deleteMe_anonimizaYMarcaDeleted() {
            User user = activeUser();
            stubActiveUser(user);
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$deleted");

            userService.deleteMe(USER_ID);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(users).save(captor.capture());
            User saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(UserStatus.DELETED);
            assertThat(saved.getIsActive()).isFalse();
            assertThat(saved.getAvatarUrl()).isNull();
            assertThat(saved.getUsername()).isEqualTo("deleted-" + USER_ID);
            assertThat(saved.getEmail()).isEqualTo("deleted-" + USER_ID + "@deleted.local");
            assertThat(saved.getPasswordHash()).isEqualTo("$2a$deleted");
        }
    }
}
