#!/usr/bin/env bash
# smoke-test.sh — post-deploy verification
#
# Usage:
#   bash bin/smoke-test.sh https://your-app.onrender.com
#
# Exit code 0 = all checks pass; non-zero = something is broken.

set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
PASS=0
FAIL=0

green() { printf "\033[32m✓ %s\033[0m\n" "$1"; }
red()   { printf "\033[31m✗ %s\033[0m\n" "$1"; }

check() {
  local label="$1"
  local actual="$2"
  local expected="$3"
  if [ "$actual" = "$expected" ]; then
    green "$label"
    PASS=$((PASS + 1))
  else
    red "$label (got '$actual', expected '$expected')"
    FAIL=$((FAIL + 1))
  fi
}

echo "Running smoke tests against $BASE_URL"
echo "────────────────────────────────────"

# 1. Health check
HEALTH=$(curl -sf "$BASE_URL/actuator/health" | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])" 2>/dev/null || echo "FAILED")
check "Health endpoint returns UP" "$HEALTH" "UP"

# 2. Shorten a URL
SHORTEN_RESP=$(curl -sf -X POST "$BASE_URL/api/v1/urls" \
  -H "Content-Type: application/json" \
  -d '{"longUrl": "https://example.com/smoke-test"}' 2>/dev/null || echo "{}")

SHORT_CODE=$(echo "$SHORTEN_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('shortCode',''))" 2>/dev/null || echo "")
check "POST /api/v1/urls returns a shortCode" "$([ -n "$SHORT_CODE" ] && echo 'ok' || echo '')" "ok"

# 3. Redirect resolves
if [ -n "$SHORT_CODE" ]; then
  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/$SHORT_CODE")
  check "GET /$SHORT_CODE returns 302" "$HTTP_CODE" "302"
fi

# 4. Unknown code returns 404
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/nonexistent-smoke-test-xyz")
check "GET /nonexistent returns 404" "$HTTP_CODE" "404"

# 5. Invalid URL returns 400
HTTP_CODE=$(curl -sf -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/v1/urls" \
  -H "Content-Type: application/json" \
  -d '{"longUrl": "not-a-url"}' 2>/dev/null || echo "400")
check "POST invalid URL returns 400" "$HTTP_CODE" "400"

# 6. Rate-limit headers present
RL_HEADER=$(curl -sf -X POST "$BASE_URL/api/v1/urls" \
  -H "Content-Type: application/json" \
  -d '{"longUrl": "https://example.com/rl-header-check"}' \
  -D - 2>/dev/null | grep -i "x-ratelimit-limit" | head -1 | tr -d '\r')
check "X-RateLimit-Limit header present" "$([ -n "$RL_HEADER" ] && echo 'ok' || echo '')" "ok"

# Summary
echo "────────────────────────────────────"
echo "Results: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
