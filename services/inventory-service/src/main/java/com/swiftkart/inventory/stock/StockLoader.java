package com.swiftkart.inventory.stock;

import com.swiftkart.inventory.store.StoreRepository;
import com.swiftkart.inventory.store.StoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Seeds Redis from the Postgres ledger on start-up. SETNX means a restart never overwrites live
 * counters (and the reservations they reflect); a fresh Redis is rebuilt from the ledger.
 */
@Component
public class StockLoader {

    private static final Logger log = LoggerFactory.getLogger(StockLoader.class);

    private final StoreRepository stores;
    private final StoreService storeService;
    private final StringRedisTemplate redis;

    public StockLoader(StoreRepository stores, StoreService storeService, StringRedisTemplate redis) {
        this.stores = stores;
        this.storeService = storeService;
        this.redis = redis;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void load() {
        storeService.indexStores();
        int seeded = 0;
        for (StoreRepository.StockRow row : stores.allStock()) {
            Boolean set = redis.opsForValue().setIfAbsent(Keys.stock(row.storeId(), row.sku()), String.valueOf(row.onHand()));
            redis.opsForValue().setIfAbsent(Keys.reserved(row.storeId(), row.sku()), "0");
            if (Boolean.TRUE.equals(set)) seeded++;
        }
        for (var store : stores.stores()) {
            String key = Keys.pickers(store.id());
            int pickers = stores.pickers(store.id());
            for (int i = 1; i <= pickers; i++) {
                redis.opsForZSet().addIfAbsent(key, store.id() + "-P" + i, 0);
            }
        }
        log.info("Stock counters ready ({} seeded from the ledger)", seeded);
    }
}
