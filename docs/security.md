# Security Decisions & Rationale

This document details the choices made for authentication and identity management during the URL Shortener build.

## BCrypt Work Factor (Strength 12)
The strength of BCrypt determines how many rounds of hashing are applied (2^12 = 4096 rounds). We chose strength `12` because it provides an excellent balance for modern web APIs:
- It forces a brute-force or dictionary attack to be computationally ruinous.
- It limits login processing times to approximately ~250-300ms on modern server CPUs, ensuring legitimate user latency remains largely unnoticed.
- Hardcoding the constant in `SecurityConstants` keeps this configuration standardized rather than loosely scattered.

## JWT Expiration (1 Hour)
A very common flaw in JWT implementation is an excessively long expiration time. Because JWTs are stateless, they cannot be conventionally revoked without adding state (e.g., a Redis blacklist layer) back into the architecture.
- By strictly keeping expiration to `1 hour`, the worst-case exposure period if a token is compromised is heavily minimized.
- The lack of a session identifier ensures scalability and decouples the authorization logic from stateful stores, staying true to stateless API design paradigms.

## Why No Refresh Tokens (Yet)
Refresh tokens introduce considerable complexity: requiring database storage, invalidation mechanisms (family rotations), and more comprehensive security handling (HTTP-only cookies vs. local storage). 
- **Current status:** For this MVP, users must simply log back in when the 1-hour access token expires.
- **Future Architecture:** A mature implementation would issue the 1-hour `access_token` in memory (or JSON payload), and a long-lived `refresh_token` (e.g., 7 days) securely nested in an `HttpOnly`, `Secure`, `SameSite=Strict` cookie.

## Account Enumeration Prevention
A typical vulnerability is returning distinct error messages like `"User not found"` vs. `"Incorrect password"`. Malicious actors use this to farm the database for valid emails.
- **Solution:** Our `AuthService` handles `findByEmail` empty results and password mismatch failures identically, throwing a `BadCredentialsException` utilizing a generic `"Invalid credentials"` constant.
- **Logging:** We still output explicit internal `log.warn()` messages indicating precisely *why* the login failed for observability, but the HTTP response remains opaque to the client.

## Secure Logging
- We log standard application-level `log.info("Authentication attempt for email: {}")`.
- **Never** log passwords or tokens. 

## Future Improvements
To make this truly enterprise-grade, subsequent iterations should implement:
1. **Rate Limiting Login Routes:** Block IPs failing >10 times a minute.
2. **MFA (2FA):** Integrate TOTP (Time-Based One-Time Password) via Authenticator apps.
3. **Password Resets:** Short-lived expiring email tokens stored in the DB alongside Spring Email.
4. **Token Blacklist:** Storing explicitly "logged out" JWT `jti` claims in Redis until their expiration passes.