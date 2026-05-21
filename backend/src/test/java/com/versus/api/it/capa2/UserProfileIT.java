package com.versus.api.it.capa2;

import com.versus.api.it.support.AbstractIT;
import com.versus.api.it.support.Factories;
import com.versus.api.users.UserStatus;
import com.versus.api.users.domain.User;

import jakarta.transaction.Transactional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;

@DisplayName("Capa 2 — Usuarios")
class UserProfileIT extends AbstractIT {

    @Nested
    @DisplayName("GET /api/users/me")
    class GetMe {

        @Test
        @DisplayName("U1 — Devuelve el perfil del autenticado sin campos sensibles")
        void devuelvePerfil() {
            User u = factories.user();
            http.reqAs(u)
                    .get("/api/users/me")
                    .then()
                    .statusCode(200)
                    .body("id", notNullValue())
                    .body("username", notNullValue())
                    .body("email", notNullValue())
                    .body("passwordHash", nullValue())
                    .body("role", equalTo("PLAYER"));
        }
    }

    @Nested
    @DisplayName("PUT /api/users/me")
    class UpdateMe {

        @Test
        @DisplayName("U2 — Actualiza username y persiste")
        void actualizaUsername() {
            User u = factories.user();
            String newName = "updatedName_" + UUID.randomUUID().toString().substring(0, 6);

            http.reqAs(u)
                    .body(Map.of("username", newName))
                    .put("/api/users/me")
                    .then()
                    .statusCode(200)
                    .body("username", equalTo(newName));

            http.reqAs(u)
                    .get("/api/users/me")
                    .then()
                    .body("username", equalTo(newName));
        }

        @Test
        @DisplayName("U3 — Username ya tomado devuelve 409 CONFLICT")
        void usernameTomado() {
            User u1 = factories.user();
            User u2 = factories.user();

            http.reqAs(u2)
                    .body(Map.of("username", u1.getUsername()))
                    .put("/api/users/me")
                    .then()
                    .statusCode(409)
                    .body("error", equalTo("CONFLICT"));
        }
    }

    @Nested
    @DisplayName("GET /api/users/{id}")
    class GetPublic {

        @Test
        @DisplayName("U4 — Devuelve UserPublicResponse sin email ni passwordHash")
        void devuelvePerfilPublico() {
            User u = factories.user();
            User viewer = factories.user();

            http.reqAs(viewer)
                    .get("/api/users/" + u.getId())
                    .then()
                    .statusCode(200)
                    .body("id", equalTo(u.getId().toString()))
                    .body("username", notNullValue())
                    .body("email", nullValue())
                    .body("passwordHash", nullValue());
        }

        @Test
        @DisplayName("U5 — UUID inexistente devuelve 404 NOT_FOUND")
        void uuidInexistente() {
            User viewer = factories.user();
            http.reqAs(viewer)
                    .get("/api/users/" + UUID.randomUUID())
                    .then()
                    .statusCode(404)
                    .body("error", equalTo("NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("PUT /api/users/me/password")
    class ChangePassword {

        @Test
        @DisplayName("Contraseña actual incorrecta → 401")
        void passwordIncorrecta() {
            User u = factories.user();
            http.reqAs(u)
                    .body(Map.of("currentPassword", "WrongPass!", "newPassword", "NewPass1!"))
                    .put("/api/users/me/password")
                    .then()
                    .statusCode(401);
        }
    }

    @Nested
    @DisplayName("DELETE /api/users/me")
    class DeleteMe {

        @Test
        @DisplayName("Soft delete → status DELETED, login posterior falla")
        void softDelete() {
            User u = factories.user();

            http.reqAs(u)
                    .delete("/api/users/me")
                    .then()
                    .statusCode(204);

            http.req()
                    .body(Map.of("email", u.getEmail(), "password", Factories.DEFAULT_PASSWORD))
                    .post("/api/auth/login")
                    .then()
                    .statusCode(401);
        }
    }

    @Nested
    @DisplayName("PUT /api/users/me/avatar")
    class Avatar {

        @Test
        @DisplayName("Avatar URL válida persiste y se devuelve")
        void avatarUrl() {
            User u = factories.user();
            String avatarUrl = "https://example.com/avatar.png";

            http.reqAs(u)
                    .body(Map.of("avatarUrl", avatarUrl))
                    .put("/api/users/me/avatar")
                    .then()
                    .statusCode(200)
                    .body("avatarUrl", equalTo(avatarUrl));
        }
    }
}
