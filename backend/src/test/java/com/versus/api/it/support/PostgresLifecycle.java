package com.versus.api.it.support;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.sql.Connection;

public class PostgresLifecycle implements BeforeAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        ApplicationContext ctx = SpringExtension.getApplicationContext(context);
        DataSource ds = ctx.getBean(DataSource.class);
        try (Connection c = ds.getConnection()) {
            if (!c.isValid(2)) throw new IllegalStateException("DataSource inválido");
        } catch (Exception e) {
            throw new IllegalStateException("""

                ════════════════════════════════════════════════════════════
                 No se puede conectar a Postgres de tests en localhost:5433.
                 Arranca el contenedor desde la raíz del repo:

                   docker compose -f docker-compose.test.yml --profile test up -d db-test

                 O ejecuta toda la suite dentro del contenedor:

                   docker compose -f docker-compose.test.yml --profile test up \\
                     --build --abort-on-container-exit \\
                     --exit-code-from backend-test backend-test

                ════════════════════════════════════════════════════════════
                """, e);
        }
    }
}
