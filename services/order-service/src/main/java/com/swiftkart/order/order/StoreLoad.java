package com.swiftkart.order.order;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * How many orders are ahead of you in this dark store's picking queue, right now.
 *
 * <p>Counting confirmed orders in the database lags during a burst: 50 checkouts in the same
 * second would each see an empty queue and get the same optimistic promise. Instead every order
 * joins a per-store sorted set the moment its stock is reserved and leaves it when packed (or
 * cancelled); joining returns the queue position atomically.
 */
@Component
public class StoreLoad {

    private static final long STALE_MS = 60 * 60_000L;
    private static final DefaultRedisScript<Long> JOIN = new DefaultRedisScript<>("""
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', tonumber(ARGV[2]) - tonumber(ARGV[3]))
            local ahead = redis.call('ZCARD', KEYS[1])
            redis.call('ZADD', KEYS[1], 'NX', ARGV[2], ARGV[1])
            return ahead""", Long.class);

    private final StringRedisTemplate redis;

    public StoreLoad(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private static String key(String storeId) {
        return "store-load:{" + storeId + "}";
    }

    /** Joins the queue and returns the number of orders ahead. */
    public long join(String storeId, String orderId) {
        Long ahead = redis.execute(JOIN, List.of(key(storeId)), orderId,
                String.valueOf(System.currentTimeMillis()), String.valueOf(STALE_MS));
        return ahead == null ? 0 : ahead;
    }

    public void leave(String storeId, String orderId) {
        redis.opsForZSet().remove(key(storeId), orderId);
    }
}
