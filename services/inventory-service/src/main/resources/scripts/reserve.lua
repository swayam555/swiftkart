-- Reserve every line of a cart, or nothing.
-- All keys carry the store id as a hash tag ({BLR-KOR}), so they live in one Redis Cluster slot
-- and this script stays atomic even on a sharded cluster.
--
-- KEYS[1] = resv:{store}:<orderId>      hash sku -> qty
-- KEYS[2] = resv-exp:{store}            zset orderId -> expiry millis
-- KEYS[3..] = pairs of stock:{store}:<sku>, reserved:{store}:<sku>
-- ARGV[1] = orderId, ARGV[2] = expiry millis, ARGV[3..] = pairs of sku, qty
if redis.call('EXISTS', KEYS[1]) == 1 then
    return {1}            -- idempotent retry of the same order
end

local short = {}
local lines = (#KEYS - 2) / 2
for i = 0, lines - 1 do
    local available = tonumber(redis.call('GET', KEYS[3 + 2 * i]) or '0')
    local qty = tonumber(ARGV[4 + 2 * i])
    if available < qty then
        table.insert(short, ARGV[3 + 2 * i])
    end
end
if #short > 0 then
    table.insert(short, 1, 0)
    return short
end

for i = 0, lines - 1 do
    local qty = tonumber(ARGV[4 + 2 * i])
    redis.call('DECRBY', KEYS[3 + 2 * i], qty)
    redis.call('INCRBY', KEYS[4 + 2 * i], qty)
    redis.call('HSET', KEYS[1], ARGV[3 + 2 * i], qty)
end
redis.call('ZADD', KEYS[2], ARGV[2], ARGV[1])
return {1}
