package com.vamshi.urlshortener.service;

import com.vamshi.urlshortener.dto.CreateUrlRequest;
import com.vamshi.urlshortener.dto.UrlResponse;
import com.vamshi.urlshortener.entity.Url;
import com.vamshi.urlshortener.exception.Exceptions.CustomAliasConflictException;
import com.vamshi.urlshortener.exception.Exceptions.InvalidUrlException;
import com.vamshi.urlshortener.exception.Exceptions.ShortCodeExpiredException;
import com.vamshi.urlshortener.exception.Exceptions.ShortCodeNotFoundException;
import com.vamshi.urlshortener.repository.UrlRepository;
import com.vamshi.urlshortener.util.Base62Encoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    private static final String BASE_URL = "http://localhost:8080";
    private static final long STUB_ID = 12345L;

    @Mock private UrlRepository urlRepository;
    @Mock private IdGeneratorService idGeneratorService;

    private UrlService urlService;

    @BeforeEach
    void setUp() {
        urlService = new UrlService(urlRepository, idGeneratorService, BASE_URL);
    }

    // --- shortenUrl: happy path ---

    @Test
    void shortenUrl_autoCode_returnsEncodedId() {
        when(idGeneratorService.nextId()).thenReturn(STUB_ID);
        when(urlRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UrlResponse response = urlService.shortenUrl(new CreateUrlRequest("https://example.com", null, null));

        assertThat(response.shortCode()).isEqualTo(Base62Encoder.encode(STUB_ID));
        assertThat(response.shortUrl()).isEqualTo(BASE_URL + "/" + response.shortCode());
        assertThat(response.longUrl()).isEqualTo("https://example.com");
        verify(idGeneratorService).nextId();
    }

    @Test
    void shortenUrl_customAlias_usesAlias() {
        when(urlRepository.existsByShortCode("mylink")).thenReturn(false);
        when(idGeneratorService.nextId()).thenReturn(STUB_ID);
        when(urlRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UrlResponse response = urlService.shortenUrl(
                new CreateUrlRequest("https://example.com", "mylink", null));

        assertThat(response.shortCode()).isEqualTo("mylink");
    }

    @Test
    void shortenUrl_withExpiry_propagatesExpiresAt() {
        OffsetDateTime expiry = OffsetDateTime.now().plusDays(7);
        when(idGeneratorService.nextId()).thenReturn(STUB_ID);
        when(urlRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UrlResponse response = urlService.shortenUrl(
                new CreateUrlRequest("https://example.com", null, expiry));

        assertThat(response.expiresAt()).isEqualTo(expiry);
    }

    // --- shortenUrl: validation failures ---

    @Test
    void shortenUrl_invalidUrl_throws() {
        assertThatThrownBy(() ->
                urlService.shortenUrl(new CreateUrlRequest("not-a-url", null, null)))
                .isInstanceOf(InvalidUrlException.class);
        verifyNoInteractions(idGeneratorService);
    }

    @Test
    void shortenUrl_reservedAlias_throws() {
        assertThatThrownBy(() ->
                urlService.shortenUrl(new CreateUrlRequest("https://example.com", "api", null)))
                .isInstanceOf(CustomAliasConflictException.class)
                .hasMessageContaining("reserved");
        verifyNoInteractions(idGeneratorService);
    }

    @Test
    void shortenUrl_takenAlias_throws() {
        when(urlRepository.existsByShortCode("taken")).thenReturn(true);

        assertThatThrownBy(() ->
                urlService.shortenUrl(new CreateUrlRequest("https://example.com", "taken", null)))
                .isInstanceOf(CustomAliasConflictException.class)
                .hasMessageContaining("already taken");
        verifyNoInteractions(idGeneratorService);
    }

    @Test
    void shortenUrl_reservedAlias_doesNotConsumeId() {
        // Alias validation must happen BEFORE nextId() is called — no wasted IDs on bad input.
        assertThatThrownBy(() ->
                urlService.shortenUrl(new CreateUrlRequest("https://example.com", "admin", null)))
                .isInstanceOf(CustomAliasConflictException.class);
        verifyNoInteractions(idGeneratorService);
    }

    // --- resolveShortCode ---

    @Test
    void resolveShortCode_found_returnsLongUrl() {
        Url url = Url.builder().id(1L).shortCode("abc").longUrl("https://example.com").build();
        when(urlRepository.findByShortCode("abc")).thenReturn(Optional.of(url));

        assertThat(urlService.resolveShortCode("abc")).isEqualTo("https://example.com");
    }

    @Test
    void resolveShortCode_notFound_throws() {
        when(urlRepository.findByShortCode("xyz")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> urlService.resolveShortCode("xyz"))
                .isInstanceOf(ShortCodeNotFoundException.class);
    }

    @Test
    void resolveShortCode_expired_throws() {
        Url url = Url.builder()
                .id(1L).shortCode("old").longUrl("https://example.com")
                .expiresAt(OffsetDateTime.now().minusMinutes(1))
                .build();
        when(urlRepository.findByShortCode("old")).thenReturn(Optional.of(url));

        assertThatThrownBy(() -> urlService.resolveShortCode("old"))
                .isInstanceOf(ShortCodeExpiredException.class);
    }

    @Test
    void resolveShortCode_notYetExpired_returnsLongUrl() {
        Url url = Url.builder()
                .id(1L).shortCode("ok").longUrl("https://example.com")
                .expiresAt(OffsetDateTime.now().plusHours(1))
                .build();
        when(urlRepository.findByShortCode("ok")).thenReturn(Optional.of(url));

        assertThat(urlService.resolveShortCode("ok")).isEqualTo("https://example.com");
    }
}
