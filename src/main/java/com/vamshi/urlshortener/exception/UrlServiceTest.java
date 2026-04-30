package com.vamshi.urlshortener.service;

import com.vamshi.urlshortener.dto.CreateUrlRequest;
import com.vamshi.urlshortener.exception.Exceptions.*;
import com.vamshi.urlshortener.repository.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
public class UrlServiceTest {

    @Mock private UrlRepository urlRepository;
    @Mock private IdGeneratorService idGeneratorService;
    private UrlService urlService;

    @BeforeEach
    void setUp() {
        urlService = new UrlService(urlRepository, idGeneratorService, "http://localhost:8080");
    }

    @Test
    void shouldThrowOnInvalidUrl() {
        CreateUrlRequest req = new CreateUrlRequest("not-a-url", null, null);
        assertThrows(InvalidUrlException.class, () -> urlService.shortenUrl(req));
    }

    @Test
    void shouldThrowOnReservedAlias() {
        CreateUrlRequest req = new CreateUrlRequest("https://example.com", "api", null);
        assertThrows(CustomAliasConflictException.class, () -> urlService.shortenUrl(req));
    }
}