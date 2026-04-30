/**
 * Stateless utility classes — pure functions, no Spring beans.
 *
 * Will contain:
 * - Base62Encoder   : encodes a long counter to a 7-char alphanumeric short code.
 * - IpExtractor     : pulls the real client IP from X-Forwarded-For headers.
 * - UserAgentParser : extracts device/browser from User-Agent string.
 *
 * Rules:
 * - All methods static or in a final class with private constructor.
 * - 100% unit-testable with no Spring context needed.
 */
package com.vamshi.urlshortener.util;
