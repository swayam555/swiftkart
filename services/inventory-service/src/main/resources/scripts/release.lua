-- Put a reservation's stock back on the shelf (payment failed, cart abandoned, expired).
-- Removing the order from the expiry set is the claim: if it is already gone, someone else
-- (a commit, or another sweeper replica) owns this reservation and we do nothing.
--
-- KEYS[1] = resv:{store}:<orderId>, KEYS[2] = resv-exp:{store}
-- KEYS[3..] = pairs of stock:{store}:<sku>, reserved:{store}:<sku>
-- ARGV[1] = orderId, ARGV[2..] = qty per line (same order as the key pairs)
if redis.call('ZREM', KEYS[2], ARGV[1]) == 0 then
    return 0
end
local lines = (#KEYS - 2) / 2
for i = 0, lines - 1 do
    local qty = tonumber(ARGV[2 + i])
    redis.call('INCRBY', KEYS[3 + 2 * i], qty)
    redis.call('DECRBY', KEYS[4 + 2 * i], qty)
end
redis.call('DEL', KEYS[1])
return 1
