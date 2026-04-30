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

    // Thread-safe; UrlValidator is stateless after construction.
    private static final UrlValidator URL_VALIDATOR = new UrlValidator(new String[]{"http", "https"});

    private static final Set<String> RESERVED_WORDS = Set.of(
            "api", "admin", "health", "actuator", "login", "signup", "register",
            "dashboard", "swagger", "docs", "metrics", "robots", "favicon"
    );

    private final UrlRepository urlRepository;
    private final IdGeneratorService idGeneratorService;
    private final String baseUrl;

    public UrlService(UrlRepository urlRepository,
                      IdGeneratorService idGeneratorService,
                      @Value("${app.base-url}") String baseUrl) {
        this.urlRepository = urlRepository;
        this.idGeneratorService = idGeneratorService;
        this.baseUrl = baseUrl;
    }

    @Transactional
    public UrlResponse shortenUrl(CreateUrlRequest request) {
        if (!URL_VALIDATOR.isValid(request.longUrl())) {
            throw new InvalidUrlException("longUrl is not a valid HTTP/HTTPS URL.");
        }

        boolean isCustom = request.customAlias() != null && !request.customAlias().isBlank();

        // Validate alias before consuming an ID — a rejected alias wastes no sequence numbers.
        if (isCustom) {
            String alias = request.customAlias();
            if (RESERVED_WORDS.contains(alias.toLowerCase())) {
                throw new CustomAliasConflictException("'" + alias + "' is a reserved word.");
            }
            if (urlRepository.existsByShortCode(alias)) {
                throw new CustomAliasConflictException("Custom alias '" + alias + "' is already taken.");
            }
        }

        long id = idGeneratorService.nextId();
        String shortCode = isCustom ? request.customAlias() : Base62Encoder.encode(id);

        Url url = Url.builder()
                .id(id)
                .shortCode(shortCode)
                .longUrl(request.longUrl())
                .customAlias(isCustom)
                .expiresAt(request.expiresAt())
                .build();

        Url saved = urlRepository.save(url);
        log.info("Shortened: {} -> {}", request.longUrl(), shortCode);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public String resolveShortCode(String shortCode) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortCodeNotFoundException("Short code not found: " + shortCode));

        if (url.getExpiresAt() != null && url.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new ShortCodeExpiredException("Short code has expired: " + shortCode);
        }

        log.info("Redirect: /{} -> {}", shortCode, url.getLongUrl());
        return url.getLongUrl();
    }

    private UrlResponse toResponse(Url url) {
        OffsetDateTime createdAt = url.getCreatedAt() != null ? url.getCreatedAt() : OffsetDateTime.now();
        return new UrlResponse(
                url.getShortCode(),
                baseUrl + "/" + url.getShortCode(),
                url.getLongUrl(),
                createdAt,
                url.getExpiresAt()
        );
    }
}