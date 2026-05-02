# Architecture Deep Dive

## High-Level System Design

This URL shortener is engineered to handle a heavily read-skewed workload (e.g., 99% reads / 1% writes) with ultra-low latency requirements. The architecture intentionally decouples the critical path (redirects) from the heavy processing path (analytics enrichment and DB inserts).

---

## 1. URL Shortening Flow (Write Path)

The ID generation uses a batch-allocated counter approach rather than UUIDs or Snowflakes to keep URLs as short as possible.

```mermaid
sequenceDiagram
    actor Client
    participant API as Controller
    participant Service as UrlService
    participant DB as PostgreSQL

    Client->>API: POST /api/v1/urls {longUrl}
    API->>Service: createShortUrl(longUrl, user)
    Service->>DB: Get next sequence block (if needed)
    Service->>Service: Encode ID to Base62
    Service->>DB: INSERT INTO urls
    Service-->>API: Return Shortened URL
    API-->>Client: 201 Created + JSON
```

## 2. Redirect Flow (Read Path)

The redirect flow utilizes a **Read-Through Cache**. We avoid a write-through cache because pre-warming every created URL wastes memory on links that are never clicked.

```mermaid
sequenceDiagram
    actor Client
    participant API as RedirectController
    participant Cache as Redis (TTL 1h)
    participant DB as PostgreSQL
    participant Queue as Async Analytics

    Client->>API: GET /{shortCode}
    API->>Cache: GET url:{shortCode}
    alt Cache Hit
        Cache-->>API: longUrl
    else Cache Miss
        API->>DB: SELECT long_url FROM urls
        DB-->>API: longUrl
        API->>Cache: SET url:{shortCode} (TTL 1hr)
    end
    API->>Queue: Publish ClickEvent (Fire & Forget)
    API-->>Client: 302 Found (Location: longUrl)
```

## 3. Analytics Pipeline (Async Path)

Clicks are published to a bounded in-memory `LinkedBlockingQueue`. A scheduled worker drains this queue, enriches the data, and writes to PostgreSQL using pure JDBC batches (bypassing JPA for a ~50x speedup).

```mermaid
sequenceDiagram
    participant Queue as LinkedBlockingQueue
    participant Worker as Scheduled Consumer
    participant GeoIP as MaxMind DB
    participant DB as PostgreSQL

    Worker->>Queue: Drain up to 500 events
    Worker->>Worker: Parse User-Agents (Yauaa)
    Worker->>GeoIP: Resolve IPs to Countries
    Worker->>DB: JDBC Batch Insert (clicks)
    DB-->>Worker: ACK
```