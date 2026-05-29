import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, createShortUrl, redirectParams } from './lib/config.js';

export const options = {
  scenarios: {
    warm_cache_redirect: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 100),
      timeUnit: '1s',
      duration: __ENV.DURATION || '1m',
      preAllocatedVUs: Number(__ENV.VUS || 20),
      maxVUs: Number(__ENV.MAX_VUS || 100),
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<50', 'p(99)<100'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  const shortCode = createShortUrl('warm-cache-redirect');

  for (let i = 0; i < Number(__ENV.WARMUP_REQUESTS || 20); i += 1) {
    const res = http.get(`${BASE_URL}/${shortCode}`, redirectParams());
    if (res.status !== 302) {
      throw new Error(`Cache warm-up redirect failed: status=${res.status}`);
    }
  }

  return { shortCode };
}

export default function (data) {
  const res = http.get(`${BASE_URL}/${data.shortCode}`, redirectParams());

  check(res, {
    'redirect returned 302': (r) => r.status === 302,
    'Location header present': (r) => Boolean(r.headers.Location),
  });

  sleep(0.01);
}
