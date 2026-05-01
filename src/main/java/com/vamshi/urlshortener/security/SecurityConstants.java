package com.vamshi.urlshortener.security;

public class SecurityConstants {
    // Rationale: Strength 12 strikes a good balance between brute-force resistance 
    // and login latency (~300ms per verification on modern CPUs).
    public static final int BCRYPT_STRENGTH = 12;
    public static final String INVALID_CREDENTIALS_MSG = "Invalid credentials";
}