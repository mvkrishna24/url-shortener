# LinkedIn Post Draft



🚀 I recently finished building a Distributed URL Shortener, but I wanted to treat it strictly as a system design challenge rather than a simple CRUD app. 

The goal was to mimic the SLAs of platforms like Bitly—handling 800+ RPS with sub-20ms p99 redirect latency. Getting there forced me to navigate several interesting architectural trade-offs:

⚖️ **Read-Through vs. Write-Through Caching**
Most URL shorteners have a massive "long tail"—millions of links are generated, but only a fraction are ever clicked. Instead of pre-warming the cache on creation (Write-Through) and wasting expensive Redis memory, I implemented a Read-Through strategy with a 1-hour TTL. This inherently prioritizes "hot" keys, achieving an 89% cache hit rate under load while preventing memory bloat.

⚖️ **UUIDs vs. Batch-Allocated Counters**
UUIDs prevent collisions but result in long 22-character Base62 strings. Standard database auto-increments create a severe write bottleneck. The middle ground? A centralized sequence generator in PostgreSQL that allocates blocks of 10,000 IDs to the application. The Spring Boot instances dispense these locally via lock-free `AtomicLong`s, entirely removing the DB from the critical write path.

⚖️ **Analytics Accuracy vs. Redirect Latency**
Tracking geolocation and device types synchronously would completely destroy the 20ms redirect SLA. I decoupled the analytics pipeline using a bounded in-memory queue. A background worker drains the events, enriches the data via MaxMind GeoIP, and executes pure JDBC batch inserts (yielding a ~50x throughput gain over standard Hibernate). The trade-off? If the node crashes, we might lose a few seconds of telemetry. But dropping an analytics metric is always better than dropping a user redirect.

Every endpoint is also protected by an atomic, sliding-window rate limiter powered by a custom Redis Lua script to prevent TOCTOU race conditions.

🛠️ **Tech Stack:** Java 17, Spring Boot 3, PostgreSQL, Redis, Docker, and Testcontainers (for 100% parity integration testing).

I’ve heavily documented the architecture, including sequence diagrams and the math behind the rate limiter, in the repository. If you're into backend system design, I'd love to hear your thoughts on these choices!

🐙 GitHub: [Link to your GitHub repo]
🌐 Live Demo: [Link to your live demo]

#SystemDesign #BackendEngineering #Java #SpringBoot #Redis #SoftwareEngineering #PostgreSQL

---

### Why this post works:
1. **The Hook:** It immediately signals that this isn't a beginner "to-do list" app.
2. **The Emojis & Spacing:** It's highly scannable for recruiters who are quickly scrolling.
3. **The Trade-offs:** It proves you think like a Senior Engineer. Juniors focus on *frameworks*; Seniors focus on *trade-offs*.
4. **The Metrics:** Uses your actual load-testing numbers (800 RPS, sub-20ms latency, 89% hit rate, 50x throughput gain).