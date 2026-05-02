# API Reference

Base URL: `http://localhost:8080/api/v1`

## 1. Authentication

### Login
*   **Endpoint:** `POST /auth/login`
*   **Rate Limit:** 10 req/min (IP based)

**Request Body:**
```json
{
  "username": "admin",
  "password": "password"
}
```

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR...",
  "expiresIn": 3600
}
```

---

## 2. URL Management

### Create Short URL
*   **Endpoint:** `POST /urls`
*   **Auth Required:** Yes (Bearer Token)
*   **Rate Limit:** 1,000 req/min (User based)

**Request Body:**
```json
{
  "longUrl": "https://example.com/very/long/article/path"
}
```

**Response (201 Created):**
```json
{
  "shortCode": "aB3x9",
  "longUrl": "https://example.com/very/long/article/path",
  "createdAt": "2024-05-10T12:00:00Z"
}
```

### Get URL Analytics
*   **Endpoint:** `GET /urls/{shortCode}/analytics`
*   **Auth Required:** Yes (Must be the creator)

**Response (200 OK):**
```json
{
  "totalClicks": 1250,
  "topCountries": {
    "US": 800,
    "UK": 250
  },
  "devices": {
    "Mobile": 900,
    "Desktop": 350
  }
}
```

---

## 3. Redirects

### Resolve Short URL
*   **Endpoint:** `GET /{shortCode}` (Root Path)
*   **Rate Limit:** Unlimited
*   **Response:** `302 Found` with `Location: <LongUrl>` header.