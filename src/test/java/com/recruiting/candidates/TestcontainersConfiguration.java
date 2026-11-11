package com.recruiting.candidates;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real PostgreSQL for tests, same major version as production. Search relies on PostgreSQL features (pg_trgm,
 * generated columns), so an in-memory substitute would test a different database.
 *
 * <p>One container is shared by every Spring test context in the run: it is started once, never stopped by Spring
 * ({@code destroyMethod = ""}) and removed by Testcontainers when the JVM exits.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @Bean(destroyMethod = "")
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        POSTGRES.start();
        return POSTGRES;
    }
}
