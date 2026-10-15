package com.swiftkart.inventory.stock;

import com.swiftkart.contracts.StoreEvent;
import com.swiftkart.contracts.Topics;
import com.swiftkart.inventory.config.JsonCodec;
import com.swiftkart.inventory.store.StoreRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Returns stock held by carts that were never paid for. Safe to run on every replica: the
 * release script's ZREM decides which one wins each reservation.
 */
@Component
public class ExpirySweeper {

    private final StringRedisTemplate redis;
    private final StockService stock;
    private final StoreRepository stores;
    private final KafkaTemplate<String, String> kafka;
    private final JsonCodec json;
    private volatile List<String> storeIds;

    public ExpirySweeper(StringRedisTemplate redis, StockService stock, StoreRepository stores,
                         KafkaTemplate<String, String> kafka, JsonCodec json) {
        this.redis = redis;
        this.stock = stock;
        this.stores = stores;
        this.kafka = kafka;
        this.json = json;
    }

    @Scheduled(fixedDelay = 1000)
    public void sweep() {
        if (storeIds == null) storeIds = stores.stores().stream().map(s -> s.id()).toList();
        long now = System.currentTimeMillis();
        for (String store : storeIds) {
            Set<String> expired = redis.opsForZSet().rangeByScore(Keys.expiries(store), 0, now, 0, 200);
            if (expired == null) continue;
            for (String orderId : expired) {
                if (stock.release(store, orderId)) {
                    StoreEvent e = new StoreEvent(UUID.randomUUID().toString(), StoreEvent.Type.RESERVATION_EXPIRED,
                            orderId, store, "Payment not received in time; stock returned", Instant.now());
                    kafka.send(Topics.STORE_EVENTS, orderId, json.write(e));
                }
            }
        }
    }
}
