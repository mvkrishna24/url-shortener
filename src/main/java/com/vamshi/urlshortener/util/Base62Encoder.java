package com.vamshi.urlshortener.util;

/**
 * Bijective Base62 encoder/decoder.
 *
 * <p>Alphabet: {@code 0–9 A–Z a–z} (62 characters, URL-safe without percent-encoding).
 *
 * <p>Why Base62 over Base64?
 * Base64 uses {@code +} and {@code /}, which must be percent-encoded in URLs, adding
 * characters and defeating the purpose of a short code. Base62 uses only alphanumerics
 * — safe in any URL context with no escaping.
 *
 * <p>Why not UUID?
 * A UUID in canonical form is 36 characters. A Base62-encoded 64-bit integer is at most
 * 11 characters, and typically 7–8 for IDs in the billions range.
 *
 * <p>Examples:
 * <pre>
 *   encode(0)      → "0"
 *   encode(1)      → "1"
 *   encode(61)     → "z"   (last single-char code)
 *   encode(62)     → "10"  (first two-char code; "1" × 62 + "0")
 *   encode(12345)  → "3D7"
 * </pre>
 */
public final class Base62Encoder {

    /**
     * The 62-character URL-safe alphabet. Index {@code i} maps to its character.
     * This ordering (digits first, then uppercase, then lowercase) is conventional
     * and produces codes that sort lexicographically in a predictable way.
     */
    public static final String ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    public static final int BASE = 62;

    private Base62Encoder() { /* utility class */ }

    /**
     * Encodes a non-negative {@code long} to its Base62 string representation.
     *
     * <p>This is a pure bijection — no two distinct IDs produce the same code.
     *
     * @throws IllegalArgumentException if {@code id} is negative
     */
    public static String encode(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("ID must be non-negative, got: " + id);
        }
        if (id == 0) {
            return "0";
        }
        // Build digits least-significant first, then reverse.
        // Using a char array avoids StringBuilder's internal copy on reverse.
        StringBuilder sb = new StringBuilder();
        long n = id;
        while (n > 0) {
            sb.append(ALPHABET.charAt((int) (n % BASE)));
            n /= BASE;                  // Java integer division truncates (floor for positive n)
        }
        return sb.reverse().toString();
    }

    /**
     * Decodes a Base62 string back to its original {@code long} value.
     *
     * <p>{@code decode(encode(id)) == id} for all non-negative {@code id}.
     *
     * @throws IllegalArgumentException if {@code code} is null, empty, or contains
     *                                  characters outside the Base62 alphabet
     */
    public static long decode(String code) {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("Code must not be null or empty");
        }
        long result = 0;
        for (char c : code.toCharArray()) {
            int index = ALPHABET.indexOf(c);
            if (index == -1) {
                throw new IllegalArgumentException(
                        "Invalid Base62 character '" + c + "' in code: " + code);
            }
            result = result * BASE + index;
        }
        return result;
    }
}
