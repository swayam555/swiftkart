package com.swiftkart.order.inventory;

import com.swiftkart.contracts.ReservationRequest;
import com.swiftkart.contracts.ReservationResult;
import com.swiftkart.order.config.AppProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InventoryClient {

    private final RestClient rest;
    private final CircuitBreaker breaker;
    private final Map<String, CachedCatalog> catalogCache = new ConcurrentHashMap<>();

    public InventoryClient(RestClient.Builder builder, AppProperties props, Resilience4JCircuitBreakerFactory factory) {
        JdkClientHttpRequestFactory rf = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(1)).build());
        rf.setReadTimeout(Duration.ofSeconds(5));
        this.rest = builder.baseUrl(props.inventoryUrl()).requestFactory(rf).build();
        factory.configure(b -> b
                .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(5)).build())
                .circuitBreakerConfig(CircuitBreakerConfig.custom().slidingWindowSize(20).minimumNumberOfCalls(10)
                        .failureRateThreshold(50).waitDurationInOpenState(Duration.ofSeconds(10)).build()), "inventory");
        this.breaker = factory.create("inventory");
    }

    /**
     * Reservation fails closed: if inventory can't be reached we must not take money for stock
     * we can't hold, so the customer is asked to retry instead.
     */
    public ReservationResult reserve(ReservationRequest req) {
        return breaker.run(() -> rest.post().uri("/internal/reservations").body(req).retrieve().body(ReservationResult.class),
                e -> { throw new InventoryUnavailableException(e); });
    }

    public String commit(String storeId, String orderId) {
        try {
            Map<String, String> body = rest.post().uri("/internal/reservations/{s}/{o}/commit", storeId, orderId)
                    .retrieve().body(new ParameterizedTypeReference<>() {
                    });
            return body == null ? "UNKNOWN" : body.get("outcome");
        } catch (HttpClientErrorException.Conflict e) {
            return "EXPIRED";
        } catch (org.springframework.web.client.HttpServerErrorException e) {
            return "LEDGER_REJECTED";
        }
    }

    public void release(String storeId, String orderId) {
        rest.delete().uri("/internal/reservations/{s}/{o}", storeId, orderId).retrieve().toBodilessEntity();
    }

    public Nearest nearest(double lat, double lon) {
        return rest.get().uri(u -> u.path("/api/stores/nearest").queryParam("lat", lat).queryParam("lon", lon).build())
                .retrieve().body(Nearest.class);
    }

    /** Prices come from the catalogue, never from the client. Cached for 30 s per store. */
    public Map<String, CatalogItem> catalog(String storeId) {
        CachedCatalog cached = catalogCache.get(storeId);
        if (cached != null && System.currentTimeMillis() - cached.at < 30_000) return cached.items;
        Map<String, List<CatalogItem>> body = rest.get().uri("/api/catalog?storeId={s}", storeId).retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        Map<String, CatalogItem> bySku = new ConcurrentHashMap<>();
        if (body != null) body.values().forEach(list -> list.forEach(i -> bySku.put(i.sku(), i)));
        catalogCache.put(storeId, new CachedCatalog(System.currentTimeMillis(), bySku));
        return bySku;
    }

    public void setStock(String storeId, String sku, int qty) {
        rest.post().uri("/api/inventory/ops/stores/{s}/set-stock", storeId).body(Map.of("sku", sku, "qty", qty))
                .retrieve().toBodilessEntity();
    }

    public Map<String, Object> stockOf(String storeId, String sku) {
        return rest.get().uri("/api/inventory/ops/stores/{s}/stock/{sku}", storeId, sku).retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    public List<Store> stores() {
        return rest.get().uri("/api/stores").retrieve().body(new ParameterizedTypeReference<>() {
        });
    }

    private record CachedCatalog(long at, Map<String, CatalogItem> items) {
    }

    public record CatalogItem(String sku, String name, String unit, BigDecimal price, BigDecimal mrp, String emoji,
                              int maxPerOrder, int available, boolean lowStock) {
    }

    public record Store(String id, String name, String city, double lat, double lon, double radiusKm) {
    }

    public record Nearest(Store store, double distanceKm, boolean serviceable) {
    }

    public static class InventoryUnavailableException extends RuntimeException {
        public InventoryUnavailableException(Throwable cause) {
            super("Inventory is temporarily unavailable, please try again", cause);
        }
    }
}
