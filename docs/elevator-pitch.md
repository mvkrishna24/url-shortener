# Elevator Pitches

## The 30-Second Pitch (The "Tell me about yourself/projects" hook)

"I recently architected a production-grade Distributed URL Shortener in Java and Spring Boot that handles 800 requests per second with sub-20 millisecond redirect latency. Instead of building a simple CRUD app, I focused heavily on system design—implementing a read-through Redis cache that dropped database load by 9x, and writing atomic Lua scripts for a sliding-window rate limiter. It was an incredible exercise in high-concurrency performance tuning and decoupling heavy analytics pipelines from critical read paths."

---

## The 2-Minute Walkthrough (Live Demo / High-Level Overview)

"Let me walk you through the Distributed URL Shortener I built. The goal here was to mimic the exact SLAs of platforms like Bitly.

When we create a link through the API, it's secured via stateless JWT authentication. To generate the short code, I didn't want to hit the database for every single request, so I implemented a batch-allocated sequence counter. The app grabs a block of IDs from Postgres, dispenses them via a lock-free `AtomicLong` in memory, and converts them to Base62. It's incredibly fast.

But the most interesting part is the read path. When a user clicks a link, it hits a Read-Through Redis cache. During my load testing with k6, this achieved an 89% hit rate, which means 9 out of 10 requests never even touch PostgreSQL. This is how I maintain that sub-20ms p99 latency.

At the exact same time, we need to track analytics—like what country the user is from. I couldn't let GeoIP lookups slow down the redirect, so I decoupled it. The redirect controller drops a click event into an in-memory queue and instantly returns the 302 redirect. A background worker drains that queue, does the MaxMind IP lookup, and uses pure JDBC batching to insert hundreds of records into Postgres at once, running 50 times faster than standard Hibernate.

Every endpoint is protected by a sliding-window rate limiter. To prevent race conditions under heavy load, I wrote a custom Lua script that executes the boundary checks directly inside Redis natively and atomically."

---

## The 5-Minute Deep Dive (Technical System Design Interview)

"I treated this URL Shortener as a system design problem from day one. I broke the architecture down into three distinct planes: the Write Path, the Read Path, and the Analytics Pipeline.

**1. The Write Path (URL Shortening)**
The biggest bottleneck in URL shorteners is ID generation. UUIDs are too long. Database auto-increments don't scale horizontally. I went with a centralized sequence generator with local batching. The Spring Boot application requests a block of 10,000 IDs from PostgreSQL. It holds them in an `AtomicLong` and dispenses them lock-free. When the app gets to 9,999, it fetches the next block. I encode that integer into Base62. This guarantees short strings, prevents collisions mathematically, and removes the database from the critical path.

**2. The Read Path (Redirection & Caching)**
For reads, the SLA is strict: get the user to their destination instantly. I implemented a Read-Through cache using Redis. I specifically avoided Write-Through because URL shorteners have a massive 'long tail' of data—links are created but never clicked. By using Read-Through with a 1-hour TTL, Redis only stores the 'hot' links. Under load, this gave me an 89% hit rate and reduced DB CPU utilization by 9x. I also implemented a fail-open design; if Redis crashes, the try-catch block swallows the connection error and degrades gracefully to PostgreSQL.

**3. The Protection Layer (Rate Limiting)**
I implemented a sliding-window log rate limiter. Fixed windows allow boundary bursts, and token buckets are complex to make atomic. I used Redis Sorted Sets where the score is the Unix timestamp. To prevent TOCTOU (Time of Check to Time of Use) race conditions, I wrote a Lua script. Lua executes single-threaded in Redis. In one atomic move, it evicts timestamps older than 60 seconds, counts the remainder, and admits the request. This entire check takes less than 1 millisecond.

**4. The Analytics Pipeline (Async Decoupling)**
The system tracks click counts, devices, and geographic locations. Doing this synchronously would destroy my 20ms latency goal. So, I decoupled it using the Publisher-Subscriber pattern. The web thread fires a lightweight event into an in-memory `LinkedBlockingQueue` and immediately returns the 302 response to the user. 

In the background, a scheduled thread drains the queue. It parses the User-Agent, runs the IP through a local MaxMind GeoIP database, and prepares the data. Because inserting 500 individual JPA entities is slow, I bypassed Hibernate entirely. I inject a Spring `JdbcTemplate` and use native JDBC batch updates. This sends all 500 inserts to Postgres in a single network round trip, increasing throughput by roughly 50x.

**5. Infrastructure & Testing**
I built the whole thing using Java 17 and Spring Boot 3 for its predictable G1GC performance. But the part I'm most proud of is the test suite. I integrated Testcontainers. When my CI/CD pipeline runs `mvn test`, it spins up actual Docker containers for PostgreSQL and Redis, runs the integration tests against them, and tears them down. No fragile mocked databases—I know for a fact the SQL and Lua scripts work exactly as they will in production."