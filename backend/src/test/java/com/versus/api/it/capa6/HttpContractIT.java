package com.versus.api.it.capa6;

import com.versus.api.it.support.AbstractIT;
import com.versus.api.users.domain.User;

import jakarta.transaction.Transactional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;

@DisplayName("Capa 6 — Contrato HTTP")
class HttpContractIT extends AbstractIT {

    @Value("${versus.jwt.secret}")
    String jwtSecret;

    // ── Error shape ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Forma de respuesta de error")
    class ErrorShape {

        @Test
        @DisplayName("JSON inválido en POST → 400 con shape {error, message, status}")
        void jsonInvalido() {
            http.req()
                    .contentType("application/json")
                    .body("{ this is not valid json }")
                    .post("/api/auth/register")
                    .then()
                    .statusCode(400)
                    .body("error", notNullValue())
                    .body("status", equalTo(400));
        }

        @Test
        @DisplayName("Campos requeridos faltantes → 400 VALIDATION_ERROR")
        void camposRequeridos() {
            http.req()
                    .body(Map.of("email", "test@test.com"))
                    .post("/api/auth/register")
                    .then()
                    .statusCode(400)
                    .body("error", equalTo("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Sin Authorization → 401 con shape {error: UNAUTHORIZED, status: 401}")
        void sinAuthorization() {
            http.req()
                    .get("/api/users/me")
                    .then()
                    .statusCode(401)
                    .body("error", equalTo("UNAUTHORIZED"))
                    .body("status", equalTo(401))
                    .body("message", notNullValue());
        }

        @Test
        @DisplayName("UUID mal formado en path → 400")
        void uuidMalFormado() {
            User u = factories.user();
            http.reqAs(u)
                    .get("/api/users/not-a-uuid")
                    .then()
                    .statusCode(400);
        }
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Protección de endpoints")
    class Auth {

        @Test
        @DisplayName("JWT firmado con clave incorrecta → 401")
        void jwtClaveIncorrecta() {
            // Build a token signed with a different key
            javax.crypto.SecretKey wrongKey = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                    ("wrong-secret-wrong-secret-wrong-secret-wrong-secret-aaa")
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));

            String fakeToken = io.jsonwebtoken.Jwts.builder()
                    .subject(UUID.randomUUID().toString())
                    .claim("role", "PLAYER")
                    .claim("type", "access")
                    .issuedAt(new java.util.Date())
                    .expiration(new java.util.Date(System.currentTimeMillis() + 900_000))
                    .signWith(wrongKey)
                    .compact();

            http.req()
                    .header("Authorization", "Bearer " + fakeToken)
                    .get("/api/users/me")
                    .then()
                    .statusCode(401);
        }

        @Test
        @DisplayName("PLAYER accede a /api/admin/** → 403")
        void playerEnAdmin() {
            User player = factories.user();
            http.reqAs(player)
                    .get("/api/admin/spiders")
                    .then()
                    .statusCode(403);
        }

        @Test
        @DisplayName("UUID aleatorio en /api/users/{id} → 404")
        void uuidAleatorio() {
            User u = factories.user();
            http.reqAs(u)
                    .get("/api/users/" + UUID.randomUUID())
                    .then()
                    .statusCode(404)
                    .body("error", equalTo("NOT_FOUND"));
        }
    }

    // ── Regresión ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Regresión")
    class Regression {

        @Test
        @DisplayName("PUT /api/users/me con campo extra desconocido → Jackson lo ignora (no falla)")
        void campoExtraIgnorado() {
            User u = factories.user();
            String newUsername = "newname_" + UUID.randomUUID().toString().substring(0, 6);

            http.reqAs(u)
                    .body(Map.of("username", newUsername, "unknownField", true))
                    .put("/api/users/me")
                    .then()
                    .statusCode(200)
                    .body("username", equalTo(newUsername));
        }
    }
}
