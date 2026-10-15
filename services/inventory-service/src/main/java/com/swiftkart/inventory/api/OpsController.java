package com.swiftkart.inventory.api;

import com.swiftkart.inventory.stock.Keys;
import com.swiftkart.inventory.stock.Reconciler;
import com.swiftkart.inventory.store.StoreRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory/ops")
public class OpsController {

    private static final int VELOCITY_WINDOW_MIN = 30;

    private final StoreRepository stores;
    private final StringRedisTemplate redis;
    private final JdbcClient jdbc;
    private final Reconciler reconciler;

    public OpsController(StoreRepository stores, StringRedisTemplate redis, JdbcClient jdbc, Reconciler reconciler) {
        this.stores = stores;
        this.redis = redis;
        this.jdbc = jdbc;
        this.reconciler = reconciler;
    }

    /**
     * Stock board for one dark store, with sales velocity over the last 30 minutes and "hours of
     * cover" (how long until it runs out at the current rate), sorted so the most urgent restock
     * is on top.
     */
    @GetMapping("/stores/{storeId}/stock")
    public List<StockLine> stock(@PathVariable String storeId) {
        Map<String, StoreRepository.Product> products = stores.productsBySku();
        List<StoreRepository.StockRow> ledger = stores.stock(storeId);
        long minute = System.currentTimeMillis() / 60_000;
        List<String> keys = new ArrayList<>();
        for (var row : ledger) {
            keys.add(Keys.stock(storeId, row.sku()));
            keys.add(Keys.reserved(storeId, row.sku()));
            for (int m = 0; m < VELOCITY_WINDOW_MIN; m++) keys.add(Keys.sold(storeId, row.sku(), minute - m));
        }
        List<String> values = redis.opsForValue().multiGet(keys);
        int stride = 2 + VELOCITY_WINDOW_MIN;
        List<StockLine> out = new ArrayList<>();
        for (int i = 0; i < ledger.size(); i++) {
            var row = ledger.get(i);
            int base = i * stride;
            int available = parse(values.get(base));
            int reserved = parse(values.get(base + 1));
            int sold = 0;
            for (int m = 0; m < VELOCITY_WINDOW_MIN; m++) sold += parse(values.get(base + 2 + m));
            double perHour = sold * (60.0 / VELOCITY_WINDOW_MIN);
            Double cover = perHour == 0 ? null : Math.round(available / perHour * 10) / 10.0;
            var p = products.get(row.sku());
            out.add(new StockLine(row.sku(), p.name(), p.emoji(), p.category(), row.onHand(), reserved, available,
                    sold, cover, cover != null && cover < 2));
        }
        out.sort(Comparator.comparing((StockLine l) -> l.hoursOfCover() == null ? Double.MAX_VALUE : l.hoursOfCover())
                .thenComparing(StockLine::available));
        return out;
    }

    @PostMapping("/stores/{storeId}/restock")
    @Transactional
    public Map<String, Object> restock(@PathVariable String storeId, @RequestBody Restock req) {
        if (req.qty() <= 0 || req.qty() > 1000) throw new IllegalArgumentException("qty must be 1-1000");
        int updated = jdbc.sql("UPDATE store_stock SET on_hand = on_hand + ? WHERE store_id = ? AND sku = ?")
                .params(req.qty(), storeId, req.sku()).update();
        if (updated == 0) throw new IllegalArgumentException("Unknown store or SKU");
        Long available = redis.opsForValue().increment(Keys.stock(storeId, req.sku()), req.qty());
        return Map.of("sku", req.sku(), "available", available);
    }

    /** Resets one product's stock to a fixed level, used by the flash-sale demo. */
    @PostMapping("/stores/{storeId}/set-stock")
    @Transactional
    public Map<String, Object> setStock(@PathVariable String storeId, @RequestBody Restock req) {
        jdbc.sql("UPDATE store_stock SET on_hand = ? WHERE store_id = ? AND sku = ?").params(req.qty(), storeId, req.sku()).update();
        redis.opsForValue().set(Keys.stock(storeId, req.sku()), String.valueOf(req.qty()));
        redis.opsForValue().set(Keys.reserved(storeId, req.sku()), "0");
        return Map.of("sku", req.sku(), "available", req.qty());
    }

    /** Ledger and Redis view of one product, used to verify the flash-sale outcome. */
    @GetMapping("/stores/{storeId}/stock/{sku}")
    public Map<String, Object> one(@PathVariable String storeId, @PathVariable String sku) {
        Integer onHand = jdbc.sql("SELECT on_hand FROM store_stock WHERE store_id = ? AND sku = ?")
                .params(storeId, sku).query(Integer.class).single();
        return Map.of("sku", sku, "ledgerOnHand", onHand,
                "redisAvailable", parse(redis.opsForValue().get(Keys.stock(storeId, sku))),
                "reserved", parse(redis.opsForValue().get(Keys.reserved(storeId, sku))));
    }

    @GetMapping("/reconciliation")
    public Reconciler.Report reconciliation() {
        return reconciler.lastReport();
    }

    @PostMapping("/reconciliation/run")
    public Reconciler.Report reconcileNow() {
        return reconciler.run();
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> bad(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    private static int parse(String v) {
        return v == null ? 0 : Integer.parseInt(v);
    }

    public record Restock(String sku, int qty) {
    }

    public record StockLine(String sku, String name, String emoji, String category, int ledgerOnHand, int reserved,
                            int available, int soldLast30Min, Double hoursOfCover, boolean restockSoon) {
    }
}
