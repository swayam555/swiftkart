-- Step 1 of commit: take the reservation away from the expiry sweeper.
-- KEYS[1] = resv:{store}:<orderId>, KEYS[2] = resv-exp:{store}; ARGV[1] = orderId, ARGV[2] = now millis
-- Returns 1 if this caller now owns the commit (or already did, on a retry), 0 if it expired.
-- The claim time is stamped so the reconciler can clean up a commit that died half-way.
if redis.call('ZREM', KEYS[2], ARGV[1]) == 1 then
    redis.call('HSET', KEYS[1], '__claimedAt', ARGV[2])
    return 1
end
if redis.call('EXISTS', KEYS[1]) == 1 then
    return 1
end
return 0
