package com.vamshi.urlshortener.service;

import com.vamshi.urlshortener.dto.CreateUrlRequest;
import com.vamshi.urlshortener.dto.UrlResponse;
import com.vamshi.urlshortener.entity.Url;
import com.vamshi.urlshortener.exception.Exceptions.*;
import com.vamshi.urlshortener.repository.UrlRepository;
import com.vamshi.urlshortener.util.Base62Encoder;
import org.apache.commons.validator.routines.UrlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Set;

@Service
public class UrlService {
    private static final Logger log = LoggerFactory.getLogger(UrlService.class);
    private static final Set<String> RESERVED_WORDS = Set.of(
            "api", "admin", "health", "actuator", "login", "signup", "register",
            "dashboard", "swagger", "docs", "metrics", "robots", "favicon"
    );

    private final UrlRepository urlRepository;
    private final IdGeneratorService idGeneratorService;
    private final String baseUrl;

    public UrlService(UrlRepository urlRepository, IdGeneratorService idGeneratorService, @Value("${app.base-url}") String baseUrl) {
        this.urlRepository = urlRepository;
        this.idGeneratorService = idGeneratorService;
        this.baseUrl = baseUrl;
    }

    @Transactional
    public UrlResponse shortenUrl(CreateUrlRequest request) {
        UrlValidator validator = new UrlValidator(new String[]{"http", "https"});
        if (!validator.isValid(request.longUrl())) {
            throw new InvalidUrlException("Invalid longUrl format.");
        }

        String shortCode;
        boolean isCustom = request.customAlias() != null && !request.customAlias().isBlank();
        long id = idGeneratorService.generateId(); // Always grab an ID sequence to prevent primary key issues

        if (isCustom) {
            shortCode = request.customAlias();
            if (RESERVED_WORDS.contains(shortCode.toLowerCase())) {
                throw new CustomAliasConflictException("Alias is reserved.");
            }
            if (urlRepository.existsByShortCode(shortCode)) {
                throw new CustomAliasConflictException("Alias is already taken.");
            }
        } else {
            shortCode = Base62Encoder.encode(id);
        }

        Url url = Url.builder().id(id).shortCode(shortCode).longUrl(request.longUrl())
                .customAlias(isCustom).expiresAt(request.expiresAt()).build();

        Url savedUrl = urlRepository.save(url);
        log.info("Shortened URL: {} -> {}", request.longUrl(), shortCode);

        return new UrlResponse(savedUrl.getShortCode(), baseUrl + "/" + savedUrl.getShortCode(),
                savedUrl.getLongUrl(), savedUrl.getCreatedAt() != null ? savedUrl.getCreatedAt() : OffsetDateTime.now(), savedUrl.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public String resolveShortCode(String shortCode) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortCodeNotFoundException("Short code not found."));

        if (url.getExpiresAt() != null && url.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new ShortCodeExpiredException("Short code has expired.");
        }
        log.info("Resolved URL: {} -> {}", shortCode, url.getLongUrl());
        return url.getLongUrl();
    }
}