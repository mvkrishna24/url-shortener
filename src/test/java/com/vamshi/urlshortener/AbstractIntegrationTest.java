package com.vamshi.urlshortener;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for all integration tests.
 *
 * Declares static Testcontainers containers for PostgreSQL and Redis so each
 * container is started once per JVM and reused across all subclasses. Spring Boot's
 * @ServiceConnection wires the datasource URL and Redis host/port automatically —
 * no @DynamicPropertySource boilerplate needed.
 *
 * Subclasses add @AutoConfigureMockMvc (or not) and their own @Test methods.
 * The containers are stopped by Testcontainers' Ryuk reaper on JVM exit.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
}
