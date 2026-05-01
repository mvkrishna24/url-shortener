-- Sliding Window Log rate limiter (atomic via Redis single-threaded Lua execution).
--
-- KEYS[1]  = rate-limit key, e.g. "ratelimit:ip:127.0.0.1"
-- ARGV[1]  = current time in milliseconds (string)
-- ARGV[2]  = window size in milliseconds  (string)
-- ARGV[3]  = max requests allowed per window (string)
-- ARGV[4]  = key TTL in seconds (set to window + 1s buffer so the key self-destructs)
--
-- Returns a 3-element Redis array: {allowed, remaining, resetEpochSeconds}
--   allowed          : 1 = request is permitted, 0 = rejected
--   remaining        : quota left after this call (0 when rejected)
--   resetEpochSeconds: Unix epoch when the rate limit window next resets

local key    = KEYS[1]
local now    = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit  = tonumber(ARGV[3])
local ttl    = tonumber(ARGV[4])

-- Step 1: Evict all entries whose timestamp falls outside the current window.
--         Anything scored below (now - window) is older than one full window and
--         no longer counts toward the client's quota.
redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)

-- Step 2: Count the requests that remain inside the window.
local count = tonumber(redis.call('ZCARD', key))

-- Step 3: Compute the reset epoch.
--         On DENY  : we want the moment the *oldest* in-window entry ages out, because
--                    that is the earliest a slot opens up.  Fetch the lowest-scored member.
--         On ALLOW : the slot we are about to add expires at (now + window); use that as
--                    a conservative upper bound so the client knows when their window clears.
local reset

if count >= limit then
    local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
    if #oldest >= 2 then
        -- oldest[2] is the score (timestamp in ms) of the oldest surviving entry
        reset = math.ceil((tonumber(oldest[2]) + window) / 1000)
    else
        reset = math.ceil((now + window) / 1000)
    end
    -- Return without recording — the request is rejected
    return {0, 0, reset}
end

-- Step 4: Record this request.
--         Member = "<now>:<count>" — guaranteed unique within one atomic execution:
--         since Redis runs Lua scripts single-threaded, no two calls share the same
--         (now, count) pair against the same key.  Different timestamps give different
--         'now'; same timestamp but different arrival order gives different 'count'
--         (because each script sees the post-state of all previous scripts).
redis.call('ZADD', key, now, tostring(now) .. ':' .. tostring(count))

-- Step 5: (Re-)set the TTL so the key expires shortly after the window closes.
--         Without this, a key written during a burst would linger in Redis forever.
redis.call('EXPIRE', key, ttl)

reset = math.ceil((now + window) / 1000)
-- remaining = how many more requests the client can make before hitting the limit
return {1, limit - count - 1, reset}
