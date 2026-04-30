# Learnings Log

A living document — add an entry after each prompt/feature. The goal is to articulate WHY,
not just WHAT, so these become interview talking points.

---

## Prompt 1 — Project Scaffold & Infrastructure

### Maven parent POM vs BOM
`spring-boot-starter-parent` is a parent POM that gives you managed dependency versions AND
plugin configuration (compiler settings, surefire defaults, resource filtering).
A BOM (`import` scope in `dependencyManagement`) only gives you versions — you'd still need
to configure plugins manually. For a single-module project, parent is simpler.

### Why split `application.yml` into profiles?
`application.yml` is the base — shared config that applies everywhere.
Profile files (`application-dev.yml`, etc.) override only what differs per environment.
This way a prod deploy needs one env var change (`SPRING_PROFILES_ACTIVE=prod`), not a
different config file. Secrets always come from env vars — never committed to git.

### `open-in-view: false`
Spring Boot defaults this to `true`, which keeps the JPA EntityManager open until the HTTP
response is fully written. Convenient for lazy loading, but it means your DB connection is
held for the entire request lifecycle — terrible for throughput at scale. Always disable it
and be explicit about what you load.

### Why Caffeine (L1) + Redis (L2)?
Redis has ~1ms latency per lookup. For ultra-hot short codes (top 1% of traffic), even 1ms
adds up at 10K RPS. Caffeine is in-JVM: ~100ns. The trade-off is memory (Caffeine is per-
instance) and eventual consistency (L1 can serve stale data until TTL expires). For URL
redirects, a few seconds of staleness is acceptable.

### JJWT dependency split (api / impl / jackson)
This follows the principle of coding against an interface, not an implementation. The `api`
jar is what you compile against. The `impl` jar (runtime scope) is the actual engine — if
you ever swap JWT libraries, only the runtime scope changes, not your business code.

### Testcontainers vs mocking the database
Mocking the DB with H2 or Mockito is fast but lies to you — it hides dialect differences,
missing index behaviour, and constraint violations that only surface against a real engine.
Testcontainers spins up the exact same PostgreSQL 16 image as production. Slower cold start,
honest results.

### SecurityConfig as a placeholder
With Spring Security on the classpath, every endpoint requires auth by default. Since we
have no business logic yet, a permissive `filterChain` lets the app start cleanly without
401s everywhere. This will be replaced with the real JWT filter chain in a later prompt.

### `@EnableAsync` on the main class
Marking the application class is the convention for enabling async support.
When we add `@Async` to the click-tracking service later, Spring will proxy it and execute
on a separate thread pool — meaning HTTP redirects complete without waiting for analytics
writes. The actual thread pool tuning (`ThreadPoolTaskExecutor`) lives in config/.

---
