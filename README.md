# Distributed URL Shortener

**Author:** Martha Vamshi Krishna  
**Email:** marthavamshikrishna1024@gmail.com

## Overview
A production-grade Distributed URL Shortener in Java and Spring Boot designed to handle high concurrency with low latency. The goal of this project is to mimic the exact SLAs of platforms like Bitly, handling 800+ requests per second with sub-20 millisecond p99 redirect latency. 

Instead of building a simple CRUD app, this project focuses heavily on backend system design, performance tuning, and decoupling heavy analytics pipelines from critical read paths.

## Architecture Highlights

* **Batch-Allocated Base62 ID Generation:** Instead of using slow database sequences for every request or long UUIDs, the application requests blocks of 10,000 IDs from PostgreSQL. It holds them in an `AtomicLong` and dispenses them lock-free in memory. This mathematically guarantees collision-free short strings while removing the database from the critical write path.
* **Read-Through Redis Cache:** The read path utilizes a Read-Through caching strategy with a 1-hour TTL, achieving an 89% cache hit rate during load testing. This inherently prioritizes "hot" URLs, avoiding the memory bloat of Write-Through caching and reducing DB CPU utilization by 9x.
* **Atomic Sliding-Window Rate Limiting:** To protect the platform from abuse without introducing significant overhead, I engineered a sliding-window log rate limiter. By pushing the logic into a Redis Lua script, the system evaluates limits in a single, atomic, single-threaded operation using sorted sets, eliminating TOCTOU (time-of-check to time-of-use) race conditions with sub-1ms overhead.
* **Decoupled Async Analytics Pipeline:** Click events are dropped into a bounded in-memory `LinkedBlockingQueue` to instantly return 302 redirects. A scheduled background worker drains the queue, runs GeoIP lookups via a MaxMind database, and uses pure JDBC batching to insert hundreds of records into Postgres simultaneously—operating ~50x faster than standard Hibernate.
* **Resilience & Fail-Open Design:** The system is designed to degrade gracefully. If the Redis cluster goes offline, the cache service catches the connection exception and transparently falls back to querying PostgreSQL directly.

## Tech Stack
* **Language:** Java 17
* **Framework:** Spring Boot 3
* **Database:** PostgreSQL
* **Cache & Rate Limiting:** Redis
* **Infrastructure:** Docker
* **Testing:** Testcontainers (Integration Testing)

## API Reference

### 1. Authentication
* **Endpoint:** `POST /api/v1/auth/login`
* **Rate Limit:** 10 req/min (IP based)

### 2. URL Management
* **Endpoint:** `POST /api/v1/urls`
* **Auth Required:** Yes (Bearer Token)
* **Rate Limit:** 1,000 req/min (User based)

### 3. Redirects
* **Endpoint:** `GET /api/v1/{shortCode}`
* **Rate Limit:** Unlimited
* **Response:** `302 Found` with `Location: <LongUrl>` header.

## Getting Started
1. Ensure you have Docker installed and running.
2. Clone this repository.
3. Build the project and run tests (this will spin up Testcontainers for PostgreSQL and Redis):
   ```bash
   ./mvnw clean package
   ```
4. Run the application:
   ```bash
   ./mvnw spring-boot:run
   ```

## System Design Deep Dive
I treated this project as a high-concurrency performance tuning exercise. Feel free to explore the `/docs` folder for architecture diagrams, caching strategies, and mathematical rationales for the architectural choices.