/**
 * JWT authentication infrastructure.
 *
 * Will contain:
 * - JwtTokenProvider  : creates, signs, and validates JWT tokens.
 * - JwtAuthFilter     : OncePerRequestFilter that extracts the Bearer token,
 *                       validates it, and populates the SecurityContext.
 * - UserDetailsServiceImpl : loads UserDetails from the DB for Spring Security.
 *
 * SecurityConfig (in config/) wires these together into the filter chain.
 */
package com.vamshi.urlshortener.security;
