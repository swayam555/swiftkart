package com.swiftkart.inventory.api;

import com.swiftkart.contracts.Store;
import com.swiftkart.inventory.stock.Keys;
import com.swiftkart.inventory.store.StoreRepository;
import com.swiftkart.inventory.store.StoreService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private final StoreService stores;
    private final StoreRepository repo;
    private final StringRedisTemplate redis;

    public CatalogController(StoreService stores, StoreRepository repo, StringRedisTemplate redis) {
        this.stores = stores;
        this.repo = repo;
        this.redis = redis;
    }

    @GetMapping("/stores")
    public List<Store> all() {
        return stores.all();
    }

    @GetMapping("/stores/nearest")
    public ResponseEntity<StoreService.Serviceability> nearest(@RequestParam double lat, @RequestParam double lon) {
        return stores.nearest(lat, lon).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /** Catalogue for one store with live availability (one MGET for every product). */
    @GetMapping("/catalog")
    public Map<String, List<Item>> catalog(@RequestParam String storeId) {
        List<StoreRepository.Product> products = repo.products();
        List<String> values = redis.opsForValue().multiGet(products.stream().map(p -> Keys.stock(storeId, p.sku())).toList());
        Map<String, List<Item>> byCategory = new LinkedHashMap<>();
        for (int i = 0; i < products.size(); i++) {
            var p = products.get(i);
            int available = values.get(i) == null ? 0 : Math.max(0, Integer.parseInt(values.get(i)));
            byCategory.computeIfAbsent(p.category(), k -> new ArrayList<>())
                    .add(new Item(p.sku(), p.name(), p.unit(), p.price(), p.mrp(), p.emoji(), p.maxPerOrder(),
                            Math.min(available, 99), available > 0 && available <= 5));
        }
        return byCategory;
    }

    public record Item(String sku, String name, String unit, java.math.BigDecimal price, java.math.BigDecimal mrp,
                       String emoji, int maxPerOrder, int available, boolean lowStock) {
    }
}
