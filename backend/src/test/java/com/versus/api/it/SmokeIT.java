package com.versus.api.it;

import com.versus.api.it.support.AbstractIT;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class SmokeIT extends AbstractIT {

    @Test
    void contextLoads_andTruncateSucceeds() {
        assertThatCode(() -> cleaner.truncateAll()).doesNotThrowAnyException();
    }

    @Test
    void factoriesCreateUsers() {
        var u1 = factories.user();
        var u2 = factories.admin();
        var u3 = factories.moderator();

        assertThatCode(() -> {
            assert u1.getId() != null;
            assert u2.getId() != null;
            assert u3.getId() != null;
        }).doesNotThrowAnyException();
    }

    @Test
    void httpClientReachesServer() {
        http.req()
                .get("/api/questions/categories")
                .then()
                .statusCode(200);
    }
}
