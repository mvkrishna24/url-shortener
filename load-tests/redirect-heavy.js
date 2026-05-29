import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, redirectParams, requireEnv, SHORT_CODE } from './lib/config.js';

export const options = {
  scenarios: {
    redirect_heavy: {
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
    http_req_duration: ['p(95)<100', 'p(99)<200'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  requireEnv('SHORT_CODE', SHORT_CODE);
}

export default function () {
  const res = http.get(`${BASE_URL}/${SHORT_CODE}`, redirectParams());

  check(res, {
    'redirect returned 302': (r) => r.status === 302,
    'Location header present': (r) => Boolean(r.headers.Location),
  });

  sleep(0.01);
}
