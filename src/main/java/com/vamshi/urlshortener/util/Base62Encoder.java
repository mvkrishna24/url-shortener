package com.vamshi.urlshortener.util;

/**
 * Bijective Base62 encoder/decoder.
 *
 * <p>Alphabet: {@code 0–9 A–Z a–z} (62 characters, URL-safe without percent-encoding).
 *
 * <p>Why Base62 over Base64? Base64 uses {@code +} and {@code /}, which must be
 * percent-encoded in URLs. Base62 uses only alphanumerics — safe in any URL context.
 *
 * <p>Max encoded length for {@code Long.MAX_VALUE} (9_223_372_036_854_775_807) is 11 chars.
 * Typical IDs in the billions range encode to 7–8 chars.
 *
 * <p>Examples:
 * <pre>
 *   encode(0)      → "0"
 *   encode(61)     → "z"    (last single-char code)
 *   encode(62)     → "10"   (first two-char code)
 *   encode(12345)  → "3D7"
 * </pre>
 */
public final class Base62Encoder {

    public static final String ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    public static final int BASE = 62;

    // O(1) lookup: CHAR_TO_INDEX[c] = position in ALPHABET, or -1 for invalid chars.
    // Array size 128 covers the full ASCII range; chars >= 128 are handled inline.
    private static final int[] CHAR_TO_INDEX = new int[128];
    static {
        java.util.Arrays.fill(CHAR_TO_INDEX, -1);
        for (int i = 0; i < ALPHABET.length(); i++) {
            CHAR_TO_INDEX[ALPHABET.charAt(i)] = i;
        }
    }

    // Long.MAX_VALUE in Base62 is 11 chars: "AzL8n0Y58m7"
    private static final int MAX_ENCODED_LENGTH = 11;

    private Base62Encoder() {}

    /**
     * Encodes a non-negative {@code long} to its Base62 string representation.
     * Uses a fixed-size char buffer filled right-to-left — no reverse() needed.
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
        char[] buf = new char[MAX_ENCODED_LENGTH];
        int pos = MAX_ENCODED_LENGTH;
        long n = id;
        while (n > 0) {
            buf[--pos] = ALPHABET.charAt((int) (n % BASE));
            n /= BASE;
        }
        return new String(buf, pos, MAX_ENCODED_LENGTH - pos);
    }

    /**
     * Decodes a Base62 string back to its original {@code long} value.
     * Uses a precomputed lookup array for O(1) character resolution.
     *
     * @throws IllegalArgumentException if {@code code} is null, empty, too long,
     *                                  or contains characters outside the Base62 alphabet
     */
    public static long decode(String code) {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("Code must not be null or empty");
        }
        if (code.length() > MAX_ENCODED_LENGTH) {
            throw new IllegalArgumentException(
                    "Code too long (max " + MAX_ENCODED_LENGTH + " chars): " + code);
        }
        long result = 0;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            int index = (c < 128) ? CHAR_TO_INDEX[c] : -1;
            if (index == -1) {
                throw new IllegalArgumentException(
                        "Invalid Base62 character '" + c + "' in code: " + code);
            }
            result = result * BASE + index;
        }
        return result;
    }
}
