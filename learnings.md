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

## Prompt 2 — Database Layer (Flyway + JPA Entities + Repositories)

### Why Flyway over Liquibase?
Flyway uses plain SQL — what you write is exactly what runs against the DB. No XML/YAML
abstraction to learn, no surprises from DSL translation. Versioned filenames (V1__, V2__...)
make ordering unambiguous and diffs readable in PRs. Flyway fails fast at startup if
a previously-applied migration file has been modified (checksum mismatch), preventing
silent schema drift between environments. Most backend interviewers and DBAs recognize it.

### TIMESTAMPTZ vs TIMESTAMP
TIMESTAMP stores a "wall clock" time with no timezone info. If the DB server's timezone
changes, every stored timestamp is misinterpreted — a subtle, catastrophic bug.
TIMESTAMPTZ stores a UTC epoch and converts to/from the session timezone on read/write.
In Java: `OffsetDateTime` maps to TIMESTAMPTZ.  `LocalDateTime` maps to TIMESTAMP — never
use it for audit columns.

### Composite index (url_id, clicked_at DESC) — why it eliminates the sort step
The dashboard query is: `SELECT * FROM clicks WHERE url_id = ? ORDER BY clicked_at DESC LIMIT 100`
Without the index: seq scan all rows, filter ~0.01%, sort survivors.
With the composite index:
- B-tree leaf pages are laid out as (url_id, clicked_at DESC).
- PostgreSQL jumps directly to url_id = X, then reads exactly 100 rows in order.
- No "Sort" node in EXPLAIN ANALYZE — the index IS the sort.
The DESC direction matters: for multi-column indexes it is baked in; the planner must
match it to avoid a sort. Single-column indexes are bidirectional so DESC is optional there.

### Partial index (expires_at WHERE expires_at IS NOT NULL)
~80% of URLs never expire (expires_at IS NULL). A full index includes all those NULL rows
wasting pages and slowing scans. A partial index only covers rows the cleanup job cares
about: `WHERE expires_at < NOW()`. PostgreSQL can use the partial index because it infers
the predicate is satisfied by any row in the result set.

### ON DELETE SET NULL vs CASCADE — intentional per relationship
- `urls.user_id` SET NULL: account deletion makes links anonymous, not dead.
  CASCADE would silently break embedded links across the internet.
- `clicks.url_id` CASCADE: click rows without a parent URL are meaningless orphans.
Cascade behavior is a product/UX decision, not just a JPA detail.

### ip_address TEXT vs PostgreSQL INET
INET is the correct PostgreSQL type (enables subnet operators). The problem: Hibernate
maps `String` to VARCHAR; PostgreSQL rejects VARCHAR for an INET column with PSQLException.
The bridge is a custom `AttributeConverter<String, PGobject>` — added complexity for a
field that's only ever stored and retrieved. TEXT is the pragmatic choice here.
Migration to INET later: `ALTER TABLE clicks ALTER COLUMN ip_address TYPE INET USING ip_address::INET`

### @Builder.Default on Lombok fields with initializers
Without `@Builder.Default`, Lombok's @Builder ignores field initializers. `boolean
customAlias = false` works by accident (primitive default is false), but `List<T> urls =
new ArrayList<>()` becomes `null` — a silent NPE waiting to happen. Always annotate fields
with non-trivial initializers with `@Builder.Default`.

### Spring Data path traversal: findByUserId on a ManyToOne
`Url.user` is `private User user` (not `long userId`). Spring Data resolves `findByUserId`
by splitting: `user` field + `Id` property → JPQL `WHERE u.user.id = ?`. Hibernate
compiles this to `WHERE user_id = ?` (no JOIN) because the FK is stored directly in `urls`.
Use `findByUser_Id` (underscore) to be explicit when field name ambiguity is possible.

### YAML dotted keys must be quoted
`logging.level` entries like `com.vamshi.urlshortener: DEBUG` contain dots.
YAML parsers can misread these as nested maps. Quote with `[...]`:
`"[com.vamshi.urlshortener]": DEBUG`
Spring Boot parses both forms, but the quoted form is spec-correct and linter-clean.

---
