package com.vamshi.urlshortener;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: verifies the Spring context loads without errors.
 * This catches misconfigured beans, missing env vars with no defaults,
 * and circular dependencies before you ever hit an endpoint.
 */
@SpringBootTest
@ActiveProfiles("test")
class UrlShortenerApplicationTests {

    @Test
    void contextLoads() {
        // If the context fails to start, this test fails with a descriptive error.
    }
}
