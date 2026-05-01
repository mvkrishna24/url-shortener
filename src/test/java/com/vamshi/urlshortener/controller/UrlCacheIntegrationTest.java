package com.vamshi.urlshortener.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vamshi.urlshortener.AbstractIntegrationTest;
import com.vamshi.urlshortener.dto.CreateUrlRequest;
import com.vamshi.urlshortener.repository.UrlRepository;
import com.vamshi.urlshortener.service.UrlCacheService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves that Redis caching actually works end-to-end:
 *   - second redirect hits cache, not Postgres
 *   - cache.invalidate() causes the next redirect to go back to Postgres
 *
 * Uses @SpyBean on UrlRepository to count real DB calls without mocking them,
 * so the test data is persisted normally and the spy only adds call tracking.
 * @SpyBean forces a new Spring context (separate from UrlControllerIntegrationTest).
 */
@AutoConfigureMockMvc
class UrlCacheIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UrlCacheService urlCacheService;
    @SpyBean UrlRepository urlRepositorySpy;

    @AfterEach
    void resetSpy() {
        Mockito.reset(urlRepositorySpy);
    }

    @Test
    void secondRedirect_servesFromCache_notFromDatabase() throws Exception {
        String shortCode = createUrl("https://redis-cache-test.example.com/path");

        // First redirect: cache miss → DB queried → cache populated
        redirect(shortCode, "https://redis-cache-test.example.com/path");

        // Second redirect: cache hit → DB must NOT be queried again
        redirect(shortCode, "https://redis-cache-test.example.com/path");

        verify(urlRepositorySpy, times(1)).findByShortCode(shortCode);
    }

    @Test
    void afterInvalidation_nextRedirect_requeriesDatabase() throws Exception {
        String shortCode = createUrl("https://invalidation-test.example.com");

        // Warm the cache via first redirect
        redirect(shortCode, "https://invalidation-test.example.com");

        // Invalidate
        urlCacheService.invalidate(shortCode);

        // Next redirect must go to DB again (cache is cold)
        redirect(shortCode, "https://invalidation-test.example.com");

        // findByShortCode should have been called twice: once on cache-miss, once after invalidation
        verify(urlRepositorySpy, times(2)).findByShortCode(shortCode);
    }

    // --- helpers ---

    private String createUrl(String longUrl) throws Exception {
        String body = objectMapper.writeValueAsString(
                new CreateUrlRequest(longUrl, null, null));
        String responseJson = mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(responseJson).get("shortCode").asText();
    }

    private void redirect(String shortCode, String expectedLocation) throws Exception {
        mockMvc.perform(get("/" + shortCode))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", expectedLocation));
    }
}
