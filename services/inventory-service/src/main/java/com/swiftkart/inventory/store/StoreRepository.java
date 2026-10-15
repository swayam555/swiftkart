package com.swiftkart.inventory.store;

import com.swiftkart.contracts.Store;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
public class StoreRepository {

    private final JdbcClient jdbc;

    public StoreRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Store> stores() {
        return jdbc.sql("SELECT id, name, city, lat, lon, radius_km FROM dark_stores ORDER BY city, name")
                .query(Store.class).list();
    }

    public Optional<Store> store(String id) {
        return jdbc.sql("SELECT id, name, city, lat, lon, radius_km FROM dark_stores WHERE id = ?")
                .param(id).query(Store.class).optional();
    }

    public int pickers(String storeId) {
        return jdbc.sql("SELECT pickers FROM dark_stores WHERE id = ?").param(storeId).query(Integer.class).single();
    }

    public List<Product> products() {
        return jdbc.sql("SELECT sku, name, category, unit, price, mrp, emoji, max_per_order FROM products ORDER BY category, name")
                .query(Product.class).list();
    }

    public Map<String, Product> productsBySku() {
        return products().stream().collect(Collectors.toMap(Product::sku, Function.identity()));
    }

    public List<StockRow> stock(String storeId) {
        return jdbc.sql("SELECT store_id, sku, on_hand FROM store_stock WHERE store_id = ? ORDER BY sku")
                .param(storeId).query(StockRow.class).list();
    }

    public List<StockRow> allStock() {
        return jdbc.sql("SELECT store_id, sku, on_hand FROM store_stock").query(StockRow.class).list();
    }

    public record Product(String sku, String name, String category, String unit, BigDecimal price, BigDecimal mrp,
                          String emoji, int maxPerOrder) {
    }

    public record StockRow(String storeId, String sku, int onHand) {
    }
}
