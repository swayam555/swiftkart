package com.swiftkart.inventory.stock;

import com.swiftkart.contracts.ReservationRequest;
import com.swiftkart.contracts.ReservationResult;
import com.swiftkart.inventory.config.AppProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stock reservations.
 *
 * <p>The sellable count lives in Redis so that thousands of checkouts per second can be decided
 * without touching the database. Postgres stays the ledger: units leave it only when an order is
 * paid. A reservation moves through reserve → (claim → ledger → finalize) or → release.
 */
@Service
public class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);

    private final StringRedisTemplate redis;
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final AppProperties props;
    private final MeterRegistry meters;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> reserve;
    private final RedisScript<Long> release;
    private final RedisScript<Long> claim;
    private final RedisScript<Long> finalize;

    public StockService(StringRedisTemplate redis, JdbcClient jdbc, TransactionTemplate tx, AppProperties props,
                        MeterRegistry meters) throws IOException {
        this.redis = redis;
        this.jdbc = jdbc;
        this.tx = tx;
        this.props = props;
        this.meters = meters;
        this.reserve = new DefaultRedisScript<>(script("reserve"), List.class);
        this.release = new DefaultRedisScript<>(script("release"), Long.class);
        this.claim = new DefaultRedisScript<>(script("claim"), Long.class);
        this.finalize = new DefaultRedisScript<>(script("finalize"), Long.class);
    }

    private static String script(String name) throws IOException {
        return new ClassPathResource("scripts/" + name + ".lua").getContentAsString(StandardCharsets.UTF_8);
    }

    public ReservationResult reserve(ReservationRequest req) {
        return req.mode() == ReservationRequest.Mode.NAIVE ? reserveNaively(req) : reserveAtomically(req);
    }

    /** All-or-nothing reservation of a whole cart in one Lua script: no lost updates, no partial carts. */
    ReservationResult reserveAtomically(ReservationRequest req) {
        String store = req.storeId();
        Instant expiresAt = Instant.now().plus(props.reservationTtl());
        List<String> keys = new ArrayList<>(List.of(Keys.reservation(store, req.orderId()), Keys.expiries(store)));
        List<String> args = new ArrayList<>(List.of(req.orderId(), String.valueOf(expiresAt.toEpochMilli())));
        for (ReservationRequest.Item item : merge(req.items())) {
            keys.add(Keys.stock(store, item.sku()));
            keys.add(Keys.reserved(store, item.sku()));
            args.add(item.sku());
            args.add(String.valueOf(item.qty()));
        }
        @SuppressWarnings("unchecked")
        List<Object> out = redis.execute(reserve, keys, args.toArray());
        boolean ok = ((Number) out.get(0)).longValue() == 1;
        meters.counter("inventory.reservations", "result", ok ? "reserved" : "out_of_stock").increment();
        if (ok) return new ReservationResult(true, req.orderId(), List.of(), expiresAt);
        List<String> short_ = out.subList(1, out.size()).stream().map(Object::toString).toList();
        return new ReservationResult(false, req.orderId(), short_, null);
    }

    /**
     * DELIBERATELY BROKEN. Read the count, decide in the application, then write it back: the
     * classic check-then-act race. Under concurrency two checkouts read the same "1 left" and
     * both succeed. It exists only so the flash-sale demo can show the oversell next to the
     * atomic path. Never called by real orders.
     */
    ReservationResult reserveNaively(ReservationRequest req) {
        String store = req.storeId();
        for (ReservationRequest.Item item : req.items()) {
            String value = redis.opsForValue().get(Keys.stock(store, item.sku()));
            int available = value == null ? 0 : Integer.parseInt(value);
            if (available < item.qty()) {
                return new ReservationResult(false, req.orderId(), List.of(item.sku()), null);
            }
            sleepQuietly(3); // the round trip between "read" and "write" in a typical service
            redis.opsForValue().set(Keys.stock(store, item.sku()), String.valueOf(available - item.qty()));
        }
        Instant expiresAt = Instant.now().plus(props.reservationTtl());
        Map<String, String> hash = new LinkedHashMap<>();
        req.items().forEach(i -> {
            hash.put(i.sku(), String.valueOf(i.qty()));
            redis.opsForValue().increment(Keys.reserved(store, i.sku()), i.qty());
        });
        redis.opsForHash().putAll(Keys.reservation(store, req.orderId()), hash);
        redis.opsForZSet().add(Keys.expiries(store), req.orderId(), expiresAt.toEpochMilli());
        return new ReservationResult(true, req.orderId(), List.of(), expiresAt);
    }

    /** Returns the stock of an unpaid reservation to the shelf. False if it was already committed or released. */
    public boolean release(String store, String orderId) {
        Map<String, Integer> lines = lines(store, orderId);
        if (lines.isEmpty()) return false;
        List<String> keys = new ArrayList<>(List.of(Keys.reservation(store, orderId), Keys.expiries(store)));
        List<String> args = new ArrayList<>(List.of(orderId));
        lines.forEach((sku, qty) -> {
            keys.add(Keys.stock(store, sku));
            keys.add(Keys.reserved(store, sku));
            args.add(String.valueOf(qty));
        });
        Long released = redis.execute(release, keys, args.toArray());
        boolean ok = released != null && released == 1;
        if (ok) meters.counter("inventory.releases").increment();
        return ok;
    }

    /**
     * Commits a paid order: (1) claim the reservation so the expiry sweeper can't release it,
     * (2) decrement the Postgres ledger idempotently, (3) stop counting the units as reserved.
     * A crash between (2) and (3) leaves a small, visible drift that the reconciler repairs.
     */
    public CommitOutcome commit(String store, String orderId) {
        Long claimed = redis.execute(claim, List.of(Keys.reservation(store, orderId), Keys.expiries(store)), orderId,
                String.valueOf(System.currentTimeMillis()));
        if (claimed == null || claimed == 0) {
            Boolean done = jdbc.sql("SELECT EXISTS (SELECT 1 FROM committed_orders WHERE order_id = ?)")
                    .param(orderId).query(Boolean.class).single();
            return Boolean.TRUE.equals(done) ? CommitOutcome.ALREADY_COMMITTED : CommitOutcome.EXPIRED;
        }
        Map<String, Integer> lines = lines(store, orderId);
        try {
            ledgerDecrement(store, orderId, lines);
        } catch (IllegalStateException e) {
            // Compensation: the ledger says these units don't exist (an oversell got this far).
            // Drop the reservation without putting phantom units back on the shelf.
            finalizeReservation(store, orderId, lines);
            meters.counter("inventory.ledger_rejections").increment();
            throw e;
        }
        finalizeReservation(store, orderId, lines);
        long minute = System.currentTimeMillis() / 60_000;
        lines.forEach((sku, qty) -> {
            String soldKey = Keys.sold(store, sku, minute);
            redis.opsForValue().increment(soldKey, qty);
            redis.expire(soldKey, java.time.Duration.ofHours(2));
        });
        meters.counter("inventory.commits").increment();
        return CommitOutcome.COMMITTED;
    }

    private void ledgerDecrement(String store, String orderId, Map<String, Integer> lines) {
        tx.executeWithoutResult(s -> {
            int first = jdbc.sql("INSERT INTO committed_orders (order_id, store_id) VALUES (?, ?) ON CONFLICT DO NOTHING")
                    .params(orderId, store).update();
            if (first == 0) return;
            lines.forEach((sku, qty) -> {
                int updated = jdbc.sql("UPDATE store_stock SET on_hand = on_hand - ? WHERE store_id = ? AND sku = ? AND on_hand >= ?")
                        .params(qty, store, sku, qty).update();
                if (updated == 0) {
                    // The ledger disagrees with Redis: never let it go negative, surface it loudly.
                    throw new IllegalStateException("Ledger has fewer than " + qty + " x " + sku + " at " + store);
                }
            });
        });
    }

    /** The units stop counting as reserved and the reservation record is removed. */
    private void finalizeReservation(String store, String orderId, Map<String, Integer> lines) {
        List<String> keys = new ArrayList<>(List.of(Keys.reservation(store, orderId)));
        List<String> args = new ArrayList<>();
        lines.forEach((sku, qty) -> {
            keys.add(Keys.reserved(store, sku));
            args.add(String.valueOf(qty));
        });
        redis.execute(finalize, keys, args.toArray());
    }

    /** A paid order cancelled before it left the store: the units go back on the shelf and the ledger. */
    public void returnToShelf(String store, String orderId, Map<String, Integer> lines) {
        tx.executeWithoutResult(s -> {
            int removed = jdbc.sql("DELETE FROM committed_orders WHERE order_id = ?").param(orderId).update();
            if (removed == 0) return; // never committed, or already returned
            lines.forEach((sku, qty) -> {
                jdbc.sql("UPDATE store_stock SET on_hand = on_hand + ? WHERE store_id = ? AND sku = ?")
                        .params(qty, store, sku).update();
                redis.opsForValue().increment(Keys.stock(store, sku), qty);
            });
        });
        log.info("Returned order {} to the shelf at {}", orderId, store);
    }

    public Map<String, Integer> lines(String store, String orderId) {
        Map<Object, Object> raw = redis.opsForHash().entries(Keys.reservation(store, orderId));
        Map<String, Integer> lines = new LinkedHashMap<>();
        raw.forEach((k, v) -> {
            if (!k.toString().startsWith("__")) lines.put(k.toString(), Integer.parseInt(v.toString()));
        });
        return lines;
    }

    private static List<ReservationRequest.Item> merge(List<ReservationRequest.Item> items) {
        Map<String, Integer> merged = new LinkedHashMap<>();
        for (ReservationRequest.Item i : items) {
            if (i.qty() <= 0) throw new IllegalArgumentException("Quantity must be positive for " + i.sku());
            merged.merge(i.sku(), i.qty(), Integer::sum);
        }
        return merged.entrySet().stream().map(e -> new ReservationRequest.Item(e.getKey(), e.getValue())).toList();
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public enum CommitOutcome { COMMITTED, ALREADY_COMMITTED, EXPIRED }
}
