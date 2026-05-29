# API Reference

Local base URL:

```text
http://localhost:8080
```

Versioned API base:

```text
http://localhost:8080/api/v1
```

JSON endpoints use:

```text
Content-Type: application/json
```

Protected endpoints use:

```text
Authorization: Bearer <jwt>
```

## Authentication

### Signup

```text
POST /api/v1/auth/signup
```

Auth required: no

Request:

```json
{
  "email": "user@example.com",
  "password": "password1"
}
```

Password rule:

- At least 8 characters.
- At least one letter.
- At least one number.

Response `201 Created`:

```json
{
  "id": 1,
  "email": "user@example.com"
}
```

Common status codes:

| Status | Meaning |
|---|---|
| `201` | User created |
| `400` | Validation failed |
| `409` | Email already exists |

### Login

```text
POST /api/v1/auth/login
```

Auth required: no

Request:

```json
{
  "email": "user@example.com",
  "password": "password1"
}
```

Response `200 OK`:

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresIn": 3600,
  "user": {
    "id": 1,
    "email": "user@example.com"
  }
}
```

Common status codes:

| Status | Meaning |
|---|---|
| `200` | Login succeeded |
| `400` | Validation failed |
| `401` | Invalid credentials |

### Current User

```text
GET /api/v1/auth/me
```

Auth required: yes

Response `200 OK`:

```json
{
  "id": 1,
  "email": "user@example.com"
}
```

Common status codes:

| Status | Meaning |
|---|---|
| `200` | Current user returned |
| `401` | Missing or invalid JWT |
| `404` | JWT subject no longer maps to a user |

## URL Endpoints

### Create Short URL

```text
POST /api/v1/urls
```

Auth required: optional

If a valid JWT is provided, the URL is associated with that user. Without a JWT,
the URL is created anonymously.

Request:

```json
{
  "longUrl": "https://example.com/articles/spring-boot",
  "customAlias": "spring1",
  "expiresAt": "2030-01-01T00:00:00Z"
}
```

Fields:

| Field | Required | Notes |
|---|---|---|
| `longUrl` | yes | Must be HTTP or HTTPS, max 2048 chars |
| `customAlias` | no | 4-10 chars, letters, numbers, `_`, `-` |
| `expiresAt` | no | Must be in the future |

Response `201 Created`:

```json
{
  "shortCode": "spring1",
  "shortUrl": "http://localhost:8080/spring1",
  "longUrl": "https://example.com/articles/spring-boot",
  "createdAt": "2026-05-29T07:30:00Z",
  "expiresAt": "2030-01-01T00:00:00Z"
}
```

Common status codes:

| Status | Meaning |
|---|---|
| `201` | URL created |
| `400` | Invalid request body or invalid URL |
| `401` | Invalid JWT on a protected route; anonymous create is allowed without JWT |
| `409` | Alias is reserved or already taken |
| `429` | API rate limit exceeded |

### List Current User URLs

```text
GET /api/v1/users/me/urls?page=0&size=20
```

Auth required: yes

Response `200 OK`:

```json
{
  "content": [
    {
      "id": 1001,
      "shortCode": "g8H2",
      "longUrl": "https://example.com/articles/spring-boot",
      "customAlias": false,
      "expiresAt": null,
      "createdAt": "2026-05-29T07:30:00Z"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20
  },
  "totalElements": 1,
  "totalPages": 1
}
```

Notes:

- Results are sorted by `createdAt` descending.
- `size` is capped at 100.

Common status codes:

| Status | Meaning |
|---|---|
| `200` | Page returned |
| `401` | Missing or invalid JWT |
| `429` | API rate limit exceeded |

## Redirect Endpoint

### Redirect By Short Code

```text
GET /{shortCode}
```

Auth required: no

Example:

```bash
curl -i http://localhost:8080/spring1
```

Response `302 Found`:

```text
HTTP/1.1 302
Location: https://example.com/articles/spring-boot
```

Common status codes:

| Status | Meaning |
|---|---|
| `302` | Redirect target found |
| `404` | Short code does not exist |
| `410` | Short code exists but is expired |

Notes:

- Redirects are not rate-limited by the application interceptor.
- Redirects publish click events asynchronously.
- Analytics publishing failures are logged and do not break redirects.

## Analytics Endpoint

### Get URL Analytics

```text
GET /api/v1/urls/{shortCode}/analytics?days=7
```

Auth required: yes

Ownership required: yes. Only the user who owns the URL can view analytics.
Anonymous URLs do not have an owner and are not viewable through this endpoint.

Query parameters:

| Parameter | Default | Range |
|---|---:|---|
| `days` | `7` | `1` to `90` |

Response `200 OK`:

```json
{
  "totalClicks": 42,
  "dailyClicks": [
    {
      "click_date": "2026-05-29T00:00:00Z",
      "count": 12
    }
  ],
  "topCountries": [
    {
      "country_code": "US",
      "count": 20
    }
  ],
  "topReferrers": [
    {
      "referrer": "Direct",
      "count": 30
    }
  ],
  "deviceBreakdown": {
    "Desktop": 70.0,
    "Phone": 30.0
  }
}
```

Common status codes:

| Status | Meaning |
|---|---|
| `200` | Analytics returned |
| `400` | Invalid `days` query parameter |
| `401` | Missing or invalid JWT |
| `403` | URL is anonymous or owned by another user |
| `404` | Short code does not exist |
| `429` | API rate limit exceeded |

## Rate Limit Headers

API routes under `/api/v1/**` include rate-limit headers:

```text
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 99
X-RateLimit-Reset: 1780000000
```

On `429 Too Many Requests`, the response body follows the common error shape:

```json
{
  "timestamp": "2026-05-29T07:30:00Z",
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Limit: 100 req/min. Retry after epoch second: 1780000000",
  "path": "/api/v1/urls"
}
```

## Error Response Shape

Most JSON API errors use:

```json
{
  "timestamp": "2026-05-29T07:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "longUrl is required",
  "path": "/api/v1/urls"
}
```

Redirect `404` and `410` responses are HTML bodies because they are browser-facing.
