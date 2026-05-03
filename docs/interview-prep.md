# Interview Prep Q&A

## System Design (10 Questions)

**1. "Walk me through what happens when a user shortens a URL."**
When the `POST` request hits the controller, it first passes through the JWT authentication filter and the Redis sliding-window rate limiter. If admitted, the `UrlService` requests the next available counter from the `IdGeneratorService`. This service pulls from a pre-allocated block of numbers kept in an `AtomicLong` to avoid a DB trip. The number is encoded into Base62, the long URL and user association are persisted to PostgreSQL, and the short URL is returned. I deliberately avoid caching on the write path (write-through) because many generated URLs are never clicked.

**2. "How would you scale this to 1 billion URLs?"**
A billion URLs fundamentally changes the storage and memory requirements. I would introduce database sharding for PostgreSQL, likely hashing the Base62 ID to route reads and writes to the correct shard. The batch-allocation counter would need to be replaced by an external coordinating service like ZooKeeper or etcd to manage ID blocks across multiple app instances safely. For Redis, I'd move from a single instance to a Redis Cluster to horizontally distribute the cache footprint, and ensure memory eviction policies (like volatile-lru) are strictly tuned.

**3. "What happens if Redis goes down?"**
The system is designed to "fail open." My `UrlCacheService` wraps Redis calls in a try-catch block. If Redis throws a connection exception, the service returns `Optional.empty()` and the application gracefully falls back to querying PostgreSQL directly. Similarly, the rate limiter intercepts the exception, allows the request through, and logs a critical alert. The application will experience a latency spike and increased DB load, but the core business function—redirecting users—remains online.

**4. "Why batch counters instead of UUIDs?"**
UUIDs are 128-bit numbers, which result in 22-character Base62 strings. That completely defeats the purpose of a URL *shortener*. By using a numeric sequence encoded to Base62, the first ~14 million URLs are only 4 characters long, and the next ~900 million are 5 characters. Batching those counters (fetching 10,000 at a time from Postgres) gives me the extreme speed of UUID generation without the length penalty.

**5. "How would you migrate from in-memory queue to Kafka?"**
Currently, my async analytics pipeline uses a `LinkedBlockingQueue`. To move to Kafka, I would replace the `queue.offer()` call in the redirect path with a non-blocking `KafkaTemplate.send()` to a `click_events` topic. I'd then extract the scheduled consumer into a completely separate microservice (a Spring Kafka `@KafkaListener`). This decouples the scaling—allowing me to scale the redirect service independently from the CPU-heavy analytics enrichment service, while guaranteeing we never lose click data if the web app crashes.

**6. "What's the failure mode when an app instance crashes mid-block of IDs?"**
If an instance allocates the block `10,000 to 19,999` into memory, uses 500 of them, and then the server crashes, those remaining 9,500 IDs are lost forever. For this domain, that is a perfectly acceptable trade-off. URL IDs do not need to be strictly contiguous; they just need to be unique. Trading a few lost numbers for a massive increase in write throughput is a net win.

**7. "How do you handle short code collisions?"**
Because I use a strictly increasing sequence counter centrally managed by the database and allocated in blocks, mathematical collisions are impossible by design. However, for *custom* aliases (e.g., users requesting `/my-promo`), I enforce a `UNIQUE` constraint on the `short_code` column in PostgreSQL. If a collision happens, the DB throws a `DataIntegrityViolationException`, which my global exception handler catches and translates into a 409 Conflict response.

**8. "Walk me through the rate limiter — why sliding window?"**
I chose a sliding window log over a fixed window because fixed windows suffer from boundary bursts—a user can max out their limit at 0:59 and again at 1:01, effectively doubling their throughput in two seconds. I use Redis sorted sets where the score is the timestamp. On every request, a Lua script atomically drops records older than 60 seconds, counts the remainder, and either rejects the request or adds the new timestamp. It's perfectly exact and fits cleanly into Redis.

**9. "What if two users try to create the same custom alias simultaneously?"**
I rely on the database for concurrency control here. PostgreSQL enforces a unique constraint on the alias column. Even if two requests bypass the application-level checks at the exact same millisecond, the database uses row-level locking during the insert. The first transaction commits successfully, and the second transaction fails with a constraint violation. I catch that exception in Spring and return a clean 400 or 409 to the user.

**10. "Why did you choose read-through over write-through caching?"**
Write-through pre-warms the cache by writing to Redis during URL creation. However, the vast majority of shortened URLs are used once or never at all. Pre-warming wastes expensive Redis memory on "cold" data. Read-through only caches URLs on their first click. It inherently prioritizes hot URLs, letting me keep my Redis memory footprint small while still achieving an 89% hit rate for actual traffic.

---

## Code-Level (8 Questions)

**1. "Show me your Lua script for rate limiting and explain each line."**
*(Mental walk-through)* First, `ZREMRANGEBYSCORE key '-inf' (now - window)` removes all timestamps older than 60 seconds. Second, `ZCARD key` gets the count of remaining active requests. Third, if the count is strictly less than the limit, `ZADD key now unique_member` logs the current request. I execute this as a Lua script via Spring's `RedisTemplate` because Redis guarantees Lua scripts are executed atomically. This completely prevents the TOCTOU race condition where concurrent threads read the same `ZCARD` value.

**2. "Walk me through the JWT filter."**
My `JwtAuthenticationFilter` extends `OncePerRequestFilter`. It intercepts the request, extracts the `Authorization: Bearer` header, and parses it using the JJWT library. If the signature is valid and it hasn't expired, I extract the subject (username). I then instantiate a `UsernamePasswordAuthenticationToken`, attach it to the `SecurityContextHolder`, and call `filterChain.doFilter()`. If parsing fails, I clear the context and let the request proceed—subsequent `@PreAuthorize` annotations will block it if the route is protected.

**3. "Why AtomicLong over synchronized in IdGeneratorService?"**
Using the `synchronized` keyword blocks threads at the OS level, which causes massive context-switching overhead under high concurrency. I use `AtomicLong` because it relies on hardware-level Compare-And-Swap (CAS) instructions. When threads try to grab the next ID, they do so lock-free. In my JMH benchmarks, `AtomicLong.getAndIncrement()` drastically outperformed synchronized blocks when testing at 1,000+ RPS.

**4. "How do you handle cache invalidation on URL deletion?"**
Cache invalidation is notoriously hard, but in this case, it's deterministic. In the `deleteUrl` method, I wrap the PostgreSQL deletion and the Redis eviction in a Spring `@Transactional` block. I issue a `DELETE` to Postgres, and then immediately call `redisTemplate.delete("url:" + shortCode)`. Because my cache TTL is relatively short (1 hour), even if the Redis delete fails due to a network blip, the stale cache naturally evicts itself quickly.

**5. "Show me the SQL query for analytics aggregation."**
Instead of pulling thousands of raw clicks into Java and grouping them, I push the compute to PostgreSQL. The query looks like: `SELECT country_code, COUNT(*) FROM clicks WHERE url_id = ? GROUP BY country_code ORDER BY count DESC LIMIT 10`. I use Spring Data JPA's `@Query` projection to map this directly into a DTO. This avoids the N+1 problem and keeps the API latency extremely low for the dashboard.

**6. "How are you doing batch inserts? Why not JPA saveAll?"**
Hibernate's `saveAll()` generates a distinct `INSERT` statement for every entity, which creates massive network chatter. For high-volume click tracking, I bypassed JPA completely and injected a `JdbcTemplate`. I use `jdbcTemplate.batchUpdate(sql, batchArgs)`, which utilizes PostgreSQL's native JDBC batching to pack hundreds of inserts into a single network packet. In my testing, this increased write throughput by nearly 50x compared to Hibernate.

**7. "What happens if the geo-IP lookup fails?"**
The MaxMind GeoIP database is local and fast, but IP addresses are messy. If an IP doesn't map to a country (like a localhost IP or a new subnet), the `GeoLocationService` catches the `AddressNotFoundException` and gracefully returns a default "Unknown" value. I don't let a non-critical analytics enrichment failure throw an exception that would drop the click record entirely.

**8. "How do you test a method that uses Redis?"**
I strictly avoid `@MockBean` for Redis because mocked caches don't expose serialization or connection issues. Instead, I use Testcontainers. I annotate my integration test class with `@Testcontainers` and start a real Redis Docker image. I use `@ServiceConnection` so Spring Boot automatically points its `RedisConnectionFactory` to the mapped Docker port. My tests run against the exact same Redis engine I use in production.

---

## Trade-offs & Alternatives (7 Questions)

**1. "Why Spring Boot over Quarkus or Micronaut?"**
If cold-start times were critical—like in AWS Lambda—I would have chosen Quarkus or Go. However, this is a long-running distributed system where peak throughput and ecosystem maturity matter more than startup time. Spring Boot 3 with Java 17 and the G1GC garbage collector provides highly predictable latencies. The Spring ecosystem also allowed me to implement complex features like JWT security and Micrometer observability in a fraction of the time.

**2. "Why PostgreSQL over MongoDB or DynamoDB?"**
I considered DynamoDB for its absolute scalability. However, PostgreSQL was the pragmatic choice for a few reasons. First, I needed atomic counter generation, which Postgres handles perfectly with Sequences. Second, the analytics dashboard requires complex `GROUP BY` and `date_trunc` aggregations. Doing that over a relational database is trivial, whereas MongoDB or DynamoDB would require maintaining separate aggregate counters or deploying complex map-reduce pipelines.

**3. "Why didn't you use Kafka from day 1?"**
Engineering is about avoiding premature optimization. Kafka is amazing, but it requires provisioning a ZooKeeper/Kraft cluster, managing partition keys, and handling consumer group offsets. For v1 of this architecture targeting 800 RPS, a bounded in-memory `LinkedBlockingQueue` provided the required async decoupling without the massive operational overhead. Moving to Kafka is an easy refactor because the abstraction is already strictly defined.

**4. "What would you do differently if you started over?"**
I would implement "Link Previews" directly into the shortening flow. Right now, when a URL is created, we don't know what it points to. If I started over, I'd trigger an async worker on creation to fetch the target URL's `<title>` and OpenGraph tags, storing them in the DB. This makes the user dashboard much richer and acts as an early spam-detection mechanism.

**5. "How would your design change for 1M shortcodes/sec write load?"**
At 1 million writes per second, PostgreSQL sequences become a severe bottleneck. I would completely abandon the relational database for ID generation. Instead, I'd implement Twitter's Snowflake algorithm within the app instances themselves, generating 64-bit IDs purely in-memory based on timestamp, datacenter ID, and machine ID. I'd then encode that integer to Base62 and fire-and-forget the write to a partitioned NoSQL datastore like Cassandra.

**6. "What if I told you 99% of URLs are accessed only once — would you still cache?"**
If the distribution is truly 99% single-access, then caching actually *harms* performance because you incur the overhead of writing to Redis for an item that will never be read again, causing constant cache churn. In that scenario, I would implement a Heavy Hitter detection algorithm (like Count-Min Sketch). The system would only promote a URL into the Redis cache *after* it crosses a threshold (e.g., 5 clicks in 10 seconds), ensuring Redis only holds genuinely hot keys.

**7. "How would you add A/B testing for short URL variants?"**
I would add a `variant_id` column to the DB. When a request comes in, I would consistently hash the user's IP address (e.g., `hash(ip) % 2`) to ensure a specific user always gets the same variant. The redirect controller would read the hash result, serve the corresponding long URL variant, and append the chosen variant ID to the async click event for analytics tracking later.