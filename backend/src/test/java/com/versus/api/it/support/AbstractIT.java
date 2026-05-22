package com.versus.api.it.support;

import com.versus.api.match.MatchService;
import io.restassured.RestAssured;
import io.restassured.config.ObjectMapperConfig;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
public abstract class AbstractIT {

    @Autowired
    protected TestDatabaseCleaner cleaner;
    @Autowired
    protected HttpTestClient http;
    @Autowired
    protected Factories factories;
    @Autowired
    protected TestQueryHelper testQuery;
    @Autowired(required = false)
    protected MatchService matchService;

    @BeforeEach
    final void resetState() {
        cleaner.truncateAll();
        if (matchService != null) {
            matchService.clearLiveMatchesForTest();
        }
        RestAssured.config = RestAssured.config()
                .objectMapperConfig(new ObjectMapperConfig()
                        .defaultObjectMapperType(io.restassured.mapper.ObjectMapperType.JACKSON_2));
    }
}
