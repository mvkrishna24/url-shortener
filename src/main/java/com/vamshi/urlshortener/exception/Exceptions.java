package com.vamshi.urlshortener.exception;

// Keeping these combined here for brevity; normally you'd split these into their own files.

public class Exceptions {
    public static class CustomAliasConflictException extends RuntimeException {
        public CustomAliasConflictException(String message) { super(message); }
    }

    public static class InvalidUrlException extends RuntimeException {
        public InvalidUrlException(String message) { super(message); }
    }

    public static class ShortCodeNotFoundException extends RuntimeException {
        public ShortCodeNotFoundException(String message) { super(message); }
    }

    public static class ShortCodeExpiredException extends RuntimeException {
        public ShortCodeExpiredException(String message) { super(message); }
    }
}