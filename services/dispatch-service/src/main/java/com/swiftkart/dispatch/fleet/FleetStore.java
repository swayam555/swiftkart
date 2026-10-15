package com.swiftkart.dispatch.fleet;

import com.swiftkart.dispatch.config.JsonCodec;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Redis persistence for the fleet: the leader writes snapshots, every replica can read them. */
@Component
public class FleetStore {

    static final String RIDERS = "fleet:riders";
    static final String ORDERS = "fleet:orders";
    static final String INBOX = "dispatch:inbox";

    private final StringRedisTemplate redis;
    private final JsonCodec json;

    public FleetStore(StringRedisTemplate redis, JsonCodec json) {
        this.redis = redis;
        this.json = json;
    }

    public static String geoKey(String storeId) {
        return "riders:geo:{" + storeId + "}";
    }

    public Map<String, Rider> loadRiders() {
        Map<String, Rider> out = new LinkedHashMap<>();
        redis.opsForHash().entries(RIDERS).forEach((k, v) -> out.put(k.toString(), json.read(v.toString(), Rider.class)));
        return out;
    }

    public Map<String, DispatchOrder> loadOrders() {
        Map<String, DispatchOrder> out = new LinkedHashMap<>();
        redis.opsForHash().entries(ORDERS).forEach((k, v) -> out.put(k.toString(), json.read(v.toString(), DispatchOrder.class)));
        return out;
    }

    public void saveRiders(Collection<Rider> riders) {
        if (riders.isEmpty()) return;
        Map<String, String> hash = new HashMap<>();
        Map<String, Map<String, Point>> geo = new HashMap<>();
        for (Rider r : riders) {
            hash.put(r.id, json.write(r));
            geo.computeIfAbsent(r.storeId, k -> new HashMap<>()).put(r.id, new Point(r.lon, r.lat));
        }
        redis.opsForHash().putAll(RIDERS, hash);
        geo.forEach((store, points) -> redis.opsForGeo().add(geoKey(store), points));
    }

    public void saveOrders(Collection<DispatchOrder> orders) {
        if (orders.isEmpty()) return;
        Map<String, String> hash = new HashMap<>();
        orders.forEach(o -> hash.put(o.orderId, json.write(o)));
        redis.opsForHash().putAll(ORDERS, hash);
    }

    public void deleteOrders(Collection<String> ids) {
        if (!ids.isEmpty()) redis.opsForHash().delete(ORDERS, ids.toArray());
    }

    public Rider rider(String id) {
        Object v = redis.opsForHash().get(RIDERS, id);
        return v == null ? null : json.read(v.toString(), Rider.class);
    }

    public DispatchOrder order(String id) {
        Object v = redis.opsForHash().get(ORDERS, id);
        return v == null ? null : json.read(v.toString(), DispatchOrder.class);
    }

    public void pushInbox(String message) {
        redis.opsForList().leftPush(INBOX, message);
    }

    /** Drains up to {@code max} inbox messages, oldest first. */
    public List<String> drainInbox(int max) {
        List<String> out = redis.opsForList().rightPop(INBOX, max);
        return out == null ? List.of() : out;
    }
}
