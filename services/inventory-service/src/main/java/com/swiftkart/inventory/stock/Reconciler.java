package com.swiftkart.inventory.stock;

import com.swiftkart.inventory.store.StoreRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Checks two invariants for every store and product:
 * <ul>
 *   <li>{@code reserved} equals the sum of the live reservation records, and</li>
 *   <li>{@code available == ledgerOnHand - reserved}.</li>
 * </ul>
 * A difference seen once may just be a checkout in flight, so it is only repaired when the same
 * drift is seen on two consecutive runs.
 */
@Component
public class Reconciler {

    private static final Logger log = LoggerFactory.getLogger(Reconciler.class);

    private final StoreRepository stores;
    private final StringRedisTemplate redis;
    private final MeterRegistry meters;
    private final Map<String, Integer> suspected = new HashMap<>();
    private final AtomicReference<Report> last = new AtomicReference<>(new Report(null, 0, List.of()));

    public Reconciler(StoreRepository stores, StringRedisTemplate redis, MeterRegistry meters) {
        this.stores = stores;
        this.redis = redis;
        this.meters = meters;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public synchronized Report run() {
        List<StoreRepository.StockRow> ledger = stores.allStock();
        List<String> keys = new ArrayList<>();
        for (var row : ledger) {
            keys.add(Keys.stock(row.storeId(), row.sku()));
            keys.add(Keys.reserved(row.storeId(), row.sku()));
        }
        List<String> values = redis.opsForValue().multiGet(keys);
        Map<String, Integer> liveReserved = liveReservations();
        List<Drift> drifts = new ArrayList<>();
        for (int i = 0; i < ledger.size(); i++) {
            var row = ledger.get(i);
            int available = parse(values.get(2 * i));
            int reservedCounter = parse(values.get(2 * i + 1));
            int reserved = liveReserved.getOrDefault(row.storeId() + "/" + row.sku(), 0);
            int expected = row.onHand() - reserved;
            String id = row.storeId() + "/" + row.sku();
            if (available == expected && reservedCounter == reserved) {
                suspected.remove(id);
                continue;
            }
            int drift = (available - expected) * 100_000 + (reservedCounter - reserved);
            if (suspected.getOrDefault(id, Integer.MIN_VALUE) == drift) {
                redis.opsForValue().set(Keys.reserved(row.storeId(), row.sku()), String.valueOf(reserved));
                redis.opsForValue().set(Keys.stock(row.storeId(), row.sku()), String.valueOf(Math.max(expected, 0)));
                suspected.remove(id);
                meters.counter("inventory.reconciliation.repairs").increment();
                log.warn("Repaired stock drift {}: Redis had {}, ledger says {}", id, available, expected);
                drifts.add(new Drift(row.storeId(), row.sku(), row.onHand(), reservedCounter, reserved, available,
                        expected, "REPAIRED"));
            } else {
                suspected.put(id, drift);
                drifts.add(new Drift(row.storeId(), row.sku(), row.onHand(), reservedCounter, reserved, available,
                        expected, "WATCHING"));
            }
        }
        Report report = new Report(Instant.now(), ledger.size(), drifts);
        last.set(report);
        return report;
    }

    /**
     * Sums every live reservation record (resv:{store}:orderId hashes) per store and product.
     * A record claimed for commit more than {@link #STRANDED_AFTER_MS} ago was abandoned by a
     * commit that died half-way; it is deleted and no longer counts as reserved.
     */
    private Map<String, Integer> liveReservations() {
        Map<String, Integer> sums = new HashMap<>();
        long now = System.currentTimeMillis();
        try (var cursor = redis.scan(org.springframework.data.redis.core.ScanOptions.scanOptions()
                .match("resv:{*}:*").count(1000).build())) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                String store = key.substring(key.indexOf('{') + 1, key.indexOf('}'));
                Map<Object, Object> fields = redis.opsForHash().entries(key);
                Object claimedAt = fields.get("__claimedAt");
                if (claimedAt != null && now - Long.parseLong(claimedAt.toString()) > STRANDED_AFTER_MS) {
                    redis.delete(key);
                    meters.counter("inventory.reconciliation.stranded_reservations").increment();
                    log.warn("Removed stranded reservation {}", key);
                    continue;
                }
                fields.forEach((sku, qty) -> {
                    if (!sku.toString().startsWith("__")) {
                        sums.merge(store + "/" + sku, Integer.parseInt(qty.toString()), Integer::sum);
                    }
                });
            }
        }
        return sums;
    }

    static final long STRANDED_AFTER_MS = 5 * 60_000;

    public Report lastReport() {
        return last.get();
    }

    private static int parse(String v) {
        return v == null ? 0 : Integer.parseInt(v);
    }

    public record Drift(String storeId, String sku, int ledgerOnHand, int reservedCounter, int liveReserved,
                        int redisAvailable, int expectedAvailable, String action) {
    }

    public record Report(Instant ranAt, int checked, List<Drift> drifts) {
    }
}
