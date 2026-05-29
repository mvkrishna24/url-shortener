import http from 'k6/http';

export const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
export const AUTH_TOKEN = __ENV.AUTH_TOKEN || '';
export const SHORT_CODE = __ENV.SHORT_CODE || '';
export const LONG_URL = __ENV.LONG_URL || 'https://example.com/load-test-target';

export function requireEnv(name, value) {
  if (!value) {
    throw new Error(`${name} is required. Example: ${name}=value k6 run <script>`);
  }
}

export function jsonHeaders(extra = {}) {
  return {
    headers: {
      'Content-Type': 'application/json',
      ...extra,
    },
  };
}

export function authJsonHeaders(extra = {}) {
  requireEnv('AUTH_TOKEN', AUTH_TOKEN);
  return jsonHeaders({
    Authorization: `Bearer ${AUTH_TOKEN}`,
    ...extra,
  });
}

export function redirectParams(extraHeaders = {}) {
  return {
    redirects: 0,
    headers: {
      'User-Agent': 'k6-url-shortener-load-test/1.0',
      ...extraHeaders,
    },
  };
}

export function uniqueLongUrl(label) {
  return `${LONG_URL}?scenario=${encodeURIComponent(label)}&ts=${Date.now()}&r=${Math.random()}`;
}

export function createShortUrl(label, params = jsonHeaders()) {
  const res = http.post(
    `${BASE_URL}/api/v1/urls`,
    JSON.stringify({ longUrl: uniqueLongUrl(label) }),
    params
  );

  if (res.status !== 201) {
    throw new Error(`Failed to create short URL: status=${res.status} body=${res.body}`);
  }

  return res.json('shortCode');
}
