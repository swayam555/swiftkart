-- Step 3 of commit (after the Postgres ledger has been decremented): the units are sold, so
-- they stop counting as reserved.
-- KEYS[1] = resv:{store}:<orderId>; KEYS[2..] = reserved:{store}:<sku>; ARGV = qty per line
if redis.call('EXISTS', KEYS[1]) == 0 then
    return 0
end
for i = 2, #KEYS do
    redis.call('DECRBY', KEYS[i], tonumber(ARGV[i - 1]))
end
redis.call('DEL', KEYS[1])
return 1
