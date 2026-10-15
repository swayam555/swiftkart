package com.swiftkart.inventory.store;

import com.swiftkart.contracts.Store;
import com.swiftkart.inventory.stock.Keys;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class StoreService {

    private final StoreRepository stores;
    private final StringRedisTemplate redis;

    public StoreService(StoreRepository stores, StringRedisTemplate redis) {
        this.stores = stores;
        this.redis = redis;
    }

    public void indexStores() {
        for (Store s : stores.stores()) {
            redis.opsForGeo().add(Keys.STORES_GEO, new Point(s.lon(), s.lat()), s.id());
        }
    }

    /**
     * Nearest dark store that delivers to this point, using a Redis GEOSEARCH (geohash-indexed,
     * O(log n)): the same lookup a quick-commerce app makes every time the user opens it.
     */
    public Optional<Serviceability> nearest(double lat, double lon) {
        var results = redis.opsForGeo().radius(Keys.STORES_GEO,
                new Circle(new Point(lon, lat), new Distance(15, Metrics.KILOMETERS)),
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeDistance().sortAscending().limit(1));
        if (results == null || results.getContent().isEmpty()) return Optional.empty();
        GeoResult<RedisGeoCommands.GeoLocation<String>> hit = results.getContent().get(0);
        Store store = stores.store(hit.getContent().getName()).orElseThrow();
        double km = hit.getDistance().getValue();
        return Optional.of(new Serviceability(store, Math.round(km * 100) / 100.0, km <= store.radiusKm()));
    }

    public List<Store> all() {
        return stores.stores();
    }

    public record Serviceability(Store store, double distanceKm, boolean serviceable) {
    }
}
