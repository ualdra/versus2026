package com.versus.api.it.support;

import com.versus.api.auth.JwtService;
import com.versus.api.users.domain.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
@Profile("it")
public class HttpTestClient implements ApplicationListener<WebServerInitializedEvent> {

    private int port;

    private final JwtService jwtService;
    private final SecretKey key;

    public HttpTestClient(JwtService jwtService,
                          @Value("${versus.jwt.secret}") String secret) {
        this.jwtService = jwtService;
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        this.port = event.getWebServer().getPort();
    }

    public RequestSpecification req() {
        return RestAssured.given()
                .baseUri("http://localhost")
                .port(port)
                .contentType("application/json");
    }

    public String tokenFor(User user) {
        return jwtService.generateAccessToken(user);
    }

    public RequestSpecification reqAs(User user) {
        return req().header("Authorization", "Bearer " + tokenFor(user));
    }

    public String expiredAccessToken(User user) {
        Instant past = Instant.now().minusSeconds(60);
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .claim("type", "access")
                .issuedAt(Date.from(past.minusSeconds(60)))
                .expiration(Date.from(past))
                .signWith(key)
                .compact();
    }
}
