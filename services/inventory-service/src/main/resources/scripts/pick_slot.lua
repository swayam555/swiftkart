-- Book the picker who becomes free first. KEYS[1] = pickers:{store} (zset picker -> free-at millis)
-- ARGV[1] = now millis, ARGV[2] = pick duration millis. Returns {picker, start, end}.
local first = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
local picker, freeAt = first[1], tonumber(first[2])
local start = math.max(tonumber(ARGV[1]), freeAt)
local finish = start + tonumber(ARGV[2])
redis.call('ZADD', KEYS[1], finish, picker)
return {picker, tostring(start), tostring(finish)}
