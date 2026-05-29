package com.vamshi.urlshortener.service;

/**
 * Carries both the numeric URL id and the destination long URL from a
 * short-code resolution. The id is required to record a click event in the
 * analytics pipeline; the long URL is required to issue the 302 redirect.
 *
 * Stored in Redis as "{urlId}|{longUrl}". The pipe separator is safe because
 * long URLs are percent-encoded and cannot contain a raw pipe character.
 */
public record ResolvedUrl(Long urlId, String longUrl) {

    static final char SEPARATOR = '|';

    String serialize() {
        return urlId + String.valueOf(SEPARATOR) + longUrl;
    }

    static ResolvedUrl deserialize(String value) {
        int idx = value.indexOf(SEPARATOR);
        if (idx < 1) throw new IllegalArgumentException("Invalid cached value: " + value);
        return new ResolvedUrl(Long.parseLong(value.substring(0, idx)), value.substring(idx + 1));
    }
}
