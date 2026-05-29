package com.vamshi.urlshortener.service;

import com.vamshi.urlshortener.dto.CreateUrlRequest;
import com.vamshi.urlshortener.dto.UrlResponse;
import com.vamshi.urlshortener.entity.Url;
import com.vamshi.urlshortener.entity.User;
import com.vamshi.urlshortener.exception.Exceptions.CustomAliasConflictException;
import com.vamshi.urlshortener.exception.Exceptions.InvalidUrlException;
import com.vamshi.urlshortener.exception.Exceptions.ShortCodeExpiredException;
import com.vamshi.urlshortener.exception.Exceptions.ShortCodeNotFoundException;
import com.vamshi.urlshortener.service.ResolvedUrl;
import com.vamshi.urlshortener.repository.UrlRepository;
import com.vamshi.urlshortener.repository.UserRepository;
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
    @Mock private UserRepository userRepository;
    @Mock private IdGeneratorService idGeneratorService;
    @Mock private UrlCacheService urlCacheService;

    private UrlService urlService;

    @BeforeEach
    void setUp() {
        urlService = new UrlService(
                urlRepository, userRepository, idGeneratorService, urlCacheService,
                BASE_URL);
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

    @Test
    void shortenUrl_authenticatedUser_setsOwner() {
        User owner = User.builder().id(42L).email("owner@example.com").build();
        when(userRepository.findById(42L)).thenReturn(Optional.of(owner));
        when(idGeneratorService.nextId()).thenReturn(STUB_ID);
        when(urlRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        urlService.shortenUrl(new CreateUrlRequest("https://example.com", null, null), 42L);

        verify(urlRepository).save(argThat(url -> url.getUser() != null && url.getUser().getId().equals(42L)));
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
        assertThatThrownBy(() ->
                urlService.shortenUrl(new CreateUrlRequest("https://example.com", "admin", null)))
                .isInstanceOf(CustomAliasConflictException.class);
        verifyNoInteractions(idGeneratorService);
    }

    // --- resolveShortCode: cache hit ---

    @Test
    void resolveShortCode_cacheHit_skipsDatabase() {
        when(urlCacheService.get("abc")).thenReturn(Optional.of(new ResolvedUrl(1L, "https://example.com")));

        ResolvedUrl result = urlService.resolveShortCode("abc");
        assertThat(result.longUrl()).isEqualTo("https://example.com");
        assertThat(result.urlId()).isEqualTo(1L);
        verifyNoInteractions(urlRepository);
    }

    // --- resolveShortCode: cache miss → DB ---

    @Test
    void resolveShortCode_cacheMiss_queriesDatabaseAndPopulatesCache() {
        when(urlCacheService.get("abc")).thenReturn(Optional.empty());
        Url url = Url.builder().id(1L).shortCode("abc").longUrl("https://example.com").build();
        when(urlRepository.findByShortCode("abc")).thenReturn(Optional.of(url));

        ResolvedUrl result = urlService.resolveShortCode("abc");
        assertThat(result.longUrl()).isEqualTo("https://example.com");
        assertThat(result.urlId()).isEqualTo(1L);

        verify(urlCacheService).put("abc", new ResolvedUrl(1L, "https://example.com"));
    }

    @Test
    void resolveShortCode_notFound_throws() {
        when(urlCacheService.get("xyz")).thenReturn(Optional.empty());
        when(urlRepository.findByShortCode("xyz")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> urlService.resolveShortCode("xyz"))
                .isInstanceOf(ShortCodeNotFoundException.class);
    }

    @Test
    void resolveShortCode_expired_throws() {
        when(urlCacheService.get("old")).thenReturn(Optional.empty());
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
        when(urlCacheService.get("ok")).thenReturn(Optional.empty());
        Url url = Url.builder()
                .id(1L).shortCode("ok").longUrl("https://example.com")
                .expiresAt(OffsetDateTime.now().plusHours(1))
                .build();
        when(urlRepository.findByShortCode("ok")).thenReturn(Optional.of(url));

        assertThat(urlService.resolveShortCode("ok").longUrl()).isEqualTo("https://example.com");
    }
}
