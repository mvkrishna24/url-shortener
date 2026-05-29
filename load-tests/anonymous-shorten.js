import http from 'k6/http';
import exec from 'k6/execution';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, jsonHeaders, uniqueLongUrl } from './lib/config.js';

const urlsCreated = new Counter('urls_created');

export const options = {
  scenarios: {
    anonymous_shorten: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 20),
      timeUnit: '1s',
      duration: __ENV.DURATION || '1m',
      preAllocatedVUs: Number(__ENV.VUS || 10),
      maxVUs: Number(__ENV.MAX_VUS || 50),
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<250', 'p(99)<500'],
    checks: ['rate>0.99'],
    urls_created: ['count>0'],
  },
};

export default function () {
  const clientIp = `10.20.${exec.vu.idInTest % 255}.${(exec.scenario.iterationInTest % 250) + 1}`;
  const res = http.post(
    `${BASE_URL}/api/v1/urls`,
    JSON.stringify({ longUrl: uniqueLongUrl('anonymous-shorten') }),
    jsonHeaders({ 'X-Forwarded-For': clientIp })
  );

  check(res, {
    'created short URL': (r) => r.status === 201,
    'shortCode returned': (r) => Boolean(r.json('shortCode')),
  });

  if (res.status === 201) {
    urlsCreated.add(1);
  }

  sleep(0.1);
}
