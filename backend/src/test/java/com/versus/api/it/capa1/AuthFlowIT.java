package com.versus.api.it.capa1;

import com.versus.api.it.support.AbstractIT;
import com.versus.api.users.domain.User;
import io.restassured.response.ValidatableResponse;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;

@DisplayName("Capa 1 — Auth")
class AuthFlowIT extends AbstractIT {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ValidatableResponse register(String username, String email, String password) {
        return http.req()
                .body(Map.of("username", username, "email", email, "password", password))
                .post("/api/auth/register")
                .then();
    }

    private ValidatableResponse login(String email, String password) {
        return http.req()
                .body(Map.of("email", email, "password", password))
                .post("/api/auth/login")
                .then();
    }

    private String loginAndGetRefresh(String email, String password) {
        return login(email, password)
                .statusCode(200)
                .extract().jsonPath().getString("refreshToken");
    }

    private ValidatableResponse refresh(String refreshToken) {
        return http.req()
                .body(Map.of("refreshToken", refreshToken))
                .post("/api/auth/refresh")
                .then();
    }

    // ── Register ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Register")
    class Register {

        @Test
        @DisplayName("A1 — Registro con datos válidos devuelve 201, tokens y user")
        void registroValido() {
            register("alice", "alice@versus.test", "Password1!")
                    .statusCode(201)
                    .body("accessToken", notNullValue())
                    .body("refreshToken", notNullValue())
                    .body("user.username", equalTo("alice"))
                    .body("user.role", equalTo("PLAYER"));
        }

        @Test
        @DisplayName("A2 — Email duplicado devuelve 409 CONFLICT")
        void emailDuplicado() {
            register("user1", "dup@versus.test", "Password1!").statusCode(201);
            register("user2", "dup@versus.test", "Password1!")
                    .statusCode(409)
                    .body("error", equalTo("CONFLICT"));
        }

        @Test
        @DisplayName("A3/A4 — Body inválido devuelve 400 VALIDATION_ERROR")
        void bodyInvalido() {
            http.req()
                    .body(Map.of("username", "", "email", "not-an-email", "password", "x"))
                    .post("/api/auth/register")
                    .then()
                    .statusCode(400)
                    .body("error", equalTo("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Username duplicado devuelve 409 CONFLICT")
        void usernameDuplicado() {
            register("sameuser", "a@versus.test", "Password1!").statusCode(201);
            register("sameuser", "b@versus.test", "Password1!")
                    .statusCode(409)
                    .body("error", equalTo("CONFLICT"));
        }
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Login")
    class Login {

        @Test
        @DisplayName("A6 — Login válido devuelve 200 y tokens")
        void loginValido() {
            register("bob", "bob@versus.test", "Password1!").statusCode(201);
            login("bob@versus.test", "Password1!")
                    .statusCode(200)
                    .body("accessToken", notNullValue())
                    .body("refreshToken", notNullValue());
        }

        @Test
        @DisplayName("A7 — Contraseña incorrecta devuelve 401")
        void passwordIncorrecta() {
            register("charlie", "charlie@versus.test", "Password1!").statusCode(201);
            login("charlie@versus.test", "Wrong!")
                    .statusCode(401)
                    .body("error", equalTo("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("A8 — Email inexistente devuelve 401 (no revela cuál falla)")
        void emailInexistente() {
            login("noone@versus.test", "Password1!")
                    .statusCode(401)
                    .body("error", equalTo("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("isActive=false devuelve 401 Account disabled")
        void userInactivo() {
            User u = factories.user(b -> b.isActive(false));
            login(u.getEmail(), Factories.DEFAULT_PASSWORD)
                    .statusCode(401);
        }

        @Test
        @DisplayName("status=DELETED devuelve 401")
        void userDeleted() {
            User u = factories.user(b -> b
                    .status(com.versus.api.users.UserStatus.DELETED)
                    .isActive(false));
            login(u.getEmail(), Factories.DEFAULT_PASSWORD)
                    .statusCode(401);
        }
    }

    // ── Refresh & Logout ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Refresh")
    class Refresh {

        @Test
        @DisplayName("A10 — Refresh válido rota el token; el viejo queda revocado")
        void rotacionDeToken() {
            register("dave", "dave@versus.test", "Password1!").statusCode(201);
            String oldRefresh = loginAndGetRefresh("dave@versus.test", "Password1!");

            refresh(oldRefresh).statusCode(200)
                    .body("accessToken", notNullValue());

            refresh(oldRefresh).statusCode(401);
        }

        @Test
        @DisplayName("A11 — Refresh con token inválido devuelve 401")
        void refreshInvalido() {
            refresh("not.a.valid.jwt.token")
                    .statusCode(401)
                    .body("error", equalTo("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("A13 — Logout revoca el refresh token; uso posterior devuelve 401")
        void logoutRevoca() {
            register("eve", "eve@versus.test", "Password1!").statusCode(201);
            String refreshToken = loginAndGetRefresh("eve@versus.test", "Password1!");

            http.req()
                    .body(Map.of("refreshToken", refreshToken))
                    .post("/api/auth/logout")
                    .then().statusCode(204);

            refresh(refreshToken).statusCode(401);
        }
    }

    // ── Rutas protegidas ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Protección JWT")
    class JwtProtection {

        @Test
        @DisplayName("Sin Authorization → 401 con shape de error")
        void sinAuthorization() {
            http.req()
                    .get("/api/users/me")
                    .then()
                    .statusCode(401)
                    .body("error", equalTo("UNAUTHORIZED"))
                    .body("status", equalTo(401));
        }

        @Test
        @DisplayName("Authorization: Bearer (sin token) → 401")
        void bearerSinToken() {
            http.req()
                    .header("Authorization", "Bearer ")
                    .get("/api/users/me")
                    .then()
                    .statusCode(401);
        }

        @Test
        @DisplayName("Token JWT malformado → 401")
        void tokenMalformado() {
            http.req()
                    .header("Authorization", "Bearer xxx.invalid.xxx")
                    .get("/api/users/me")
                    .then()
                    .statusCode(401);
        }

        @Test
        @DisplayName("Access token expirado → 401")
        void tokenExpirado() {
            User u = factories.user();
            String expired = http.expiredAccessToken(u);
            http.req()
                    .header("Authorization", "Bearer " + expired)
                    .get("/api/users/me")
                    .then()
                    .statusCode(401);
        }

        @Test
        @DisplayName("PLAYER accede a /api/admin/** → 403 FORBIDDEN")
        void playerEnAdmin() {
            User player = factories.user();
            http.reqAs(player)
                    .get("/api/admin/spiders")
                    .then()
                    .statusCode(403);
        }
    }

    // import alias para usar dentro de clases internas
    static final class Factories {
        static final String DEFAULT_PASSWORD = com.versus.api.it.support.Factories.DEFAULT_PASSWORD;
    }
}
