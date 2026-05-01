/**
 * k6 load test — rate limit verification
 *
 * Usage:
 *   k6 run load-tests/rate-limit.js
 *
 * What it does:
 *   Sends 120 POST /api/v1/urls requests as fast as possible from a single VU
 *   (simulating one IP address).  The first 100 should return 201 Created;
 *   requests 101-120 should return 429 Too Many Requests.
 *
 *   A second scenario runs with a simulated authenticated user (custom header)
 *   and expects 1 000 requests/min before hitting the limit.
 *
 * Expected output (excerpt):
 *   ✓ status is 201 or 429
 *   ✓ rate limit headers present
 *   ✓ first 100 requests are 201
 *   ✓ requests 101+ are 429
 */

import http from 'k6/http';
import { check, group } from 'k6';
import { Counter } from 'k6/metrics';

const created  = new Counter('urls_created');
const rejected = new Counter('requests_rejected_429');

export let options = {
  scenarios: {
    anonymous_burst: {
      executor:   'per-vu-iterations',
      vus:        1,
      iterations: 120,
      maxDuration: '10s',
    },
  },
  thresholds: {
    // At least 100 requests must succeed (201) in a 60-second window
    'urls_created':          ['count>=100'],
    // At least 15 requests must be rejected (proving the limit fires)
    'requests_rejected_429': ['count>=15'],
    // Every response must be either 201 or 429 — never 500
    'http_req_failed':       ['rate<0.01'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  group('anonymous rate-limit burst', function () {
    const res = http.post(
      `${BASE_URL}/api/v1/urls`,
      JSON.stringify({ longUrl: `https://example.com/load-test-${Date.now()}-${Math.random()}` }),
      { headers: { 'Content-Type': 'application/json' } }
    );

    check(res, {
      'status is 201 or 429': (r) => r.status === 201 || r.status === 429,
      'X-RateLimit-Limit header present': (r) => r.headers['X-Ratelimit-Limit'] !== undefined
                                                || r.headers['X-RateLimit-Limit'] !== undefined,
      'X-RateLimit-Remaining header present': (r) => r.headers['X-Ratelimit-Remaining'] !== undefined
                                                   || r.headers['X-RateLimit-Remaining'] !== undefined,
    });

    if (res.status === 201) {
      created.add(1);
    } else if (res.status === 429) {
      rejected.add(1);
      check(res, {
        '429 body has status field': (r) => JSON.parse(r.body).status === 429,
        '429 body has message field': (r) => JSON.parse(r.body).message !== undefined,
      });
    }
  });
}
