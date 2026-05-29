/**
 * k6 load test: rate-limit abuse validation.
 *
 * This script intentionally drives one anonymous client over the configured
 * request-per-minute limit. HTTP 429 is treated as an expected response for
 * this workload; 5xx responses and unexpected statuses remain failures.
 */

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, jsonHeaders, uniqueLongUrl } from './lib/config.js';

http.setResponseCallback(http.expectedStatuses({ min: 200, max: 399 }, 429));

const urlsCreated = new Counter('urls_created');
const rejected429 = new Counter('requests_rejected_429');
const unexpectedStatus = new Counter('unexpected_status');

export const options = {
  scenarios: {
    anonymous_abuse: {
      executor: 'per-vu-iterations',
      vus: Number(__ENV.VUS || 1),
      iterations: Number(__ENV.ITERATIONS || 140),
      maxDuration: __ENV.MAX_DURATION || '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
    requests_rejected_429: ['count>0'],
    unexpected_status: ['count==0'],
  },
};

export default function () {
  const res = http.post(
    `${BASE_URL}/api/v1/urls`,
    JSON.stringify({ longUrl: uniqueLongUrl('rate-limit-abuse') }),
    jsonHeaders({ 'X-Forwarded-For': __ENV.CLIENT_IP || '198.51.100.10' })
  );

  check(res, {
    'status is 201 or 429': (r) => r.status === 201 || r.status === 429,
    'rate-limit limit header present': (r) => Boolean(r.headers['X-Ratelimit-Limit'] || r.headers['X-RateLimit-Limit']),
    'rate-limit remaining header present': (r) => Boolean(r.headers['X-Ratelimit-Remaining'] || r.headers['X-RateLimit-Remaining']),
  });

  if (res.status === 201) {
    urlsCreated.add(1);
  } else if (res.status === 429) {
    rejected429.add(1);
    check(res, {
      '429 body has status': (r) => r.json('status') === 429,
      '429 body has message': (r) => Boolean(r.json('message')),
    });
  } else {
    unexpectedStatus.add(1);
  }
}
