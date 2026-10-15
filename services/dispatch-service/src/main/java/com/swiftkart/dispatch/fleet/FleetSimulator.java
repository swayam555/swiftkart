package com.swiftkart.dispatch.fleet;

import com.swiftkart.contracts.DispatchEvent;
import com.swiftkart.contracts.OrderEvent;
import com.swiftkart.contracts.Store;
import com.swiftkart.contracts.StoreEvent;
import com.swiftkart.contracts.Topics;
import com.swiftkart.dispatch.config.AppProperties;
import com.swiftkart.dispatch.config.JsonCodec;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The dispatch brain, run by the elected leader every 250 ms:
 * drain events → assign queued orders → move riders → emit dispatch events → snapshot to Redis.
 */
@Component
public class FleetSimulator {

    private static final Logger log = LoggerFactory.getLogger(FleetSimulator.class);
    private static final String[] NAMES = {"Ravi Kumar", "Suresh Yadav", "Imran Shaikh", "Manjunath Gowda",
            "Arjun Singh", "Prakash Rao", "Vijay Patil", "Sandeep Verma", "Naveen Reddy", "Farhan Ali", "Deepak Joshi",
            "Rahul Das", "Ganesh Naik", "Karthik S", "Mohit Sharma", "Ajay Chauhan", "Salman Khan", "Vikas Gupta",
            "Raju Nair", "Santosh More", "Abdul Rahim", "Gurpreet Singh", "Anil Thakur", "Sunil Pawar"};
    private static final long DROP_HANDOVER_SIM_SECONDS = 60;
    private static final long STORE_HANDOVER_SIM_SECONDS = 20;

    private final LeaderElection leader;
    private final FleetStore store;
    private final StoreDirectory stores;
    private final KafkaTemplate<String, String> kafka;
    private final JsonCodec json;
    private final AppProperties props;
    private final StringRedisTemplate redis;
    private final MeterRegistry meters;

    private Map<String, Rider> riders;
    private Map<String, DispatchOrder> orders;
    private long lastTick;

    public FleetSimulator(LeaderElection leader, FleetStore store, StoreDirectory stores,
                          KafkaTemplate<String, String> kafka, JsonCodec json, AppProperties props,
                          StringRedisTemplate redis, MeterRegistry meters) {
        this.leader = leader;
        this.store = store;
        this.stores = stores;
        this.kafka = kafka;
        this.json = json;
        this.props = props;
        this.redis = redis;
        this.meters = meters;
    }

    @Scheduled(fixedDelay = 250)
    public synchronized void tick() {
        if (!leader.isLeader()) {
            riders = null; // drop stale state; reload from Redis if we become leader again
            return;
        }
        if (stores.all().isEmpty()) return;
        long now = System.currentTimeMillis();
        if (riders == null) {
            riders = store.loadRiders();
            orders = store.loadOrders();
            seedFleetIfEmpty();
            lastTick = now;
            log.info("Leader loaded {} riders and {} orders", riders.size(), orders.size());
        }
        long dt = Math.min(now - lastTick, 2000);
        lastTick = now;

        for (String msg : store.drainInbox(500)) handle(msg);
        assignQueued(now);
        move(now, dt);
        store.saveRiders(riders.values());
        store.saveOrders(orders.values());
        purgeClosed(now);
    }

    // ------------------------------------------------------------------ inputs

    private void handle(String msg) {
        try {
            if (msg.charAt(0) == 'O') {
                OrderEvent e = json.read(msg.substring(1), OrderEvent.class);
                if (e.type() == OrderEvent.Type.CONFIRMED && !orders.containsKey(e.orderId())) {
                    orders.put(e.orderId(), new DispatchOrder(e.orderId(), e.storeId(), e.customerLat(), e.customerLon(),
                            e.promisedBy() == null ? 0 : e.promisedBy().toEpochMilli()));
                } else if (e.type() == OrderEvent.Type.CANCELLED) {
                    cancel(e.orderId());
                }
            } else {
                StoreEvent e = json.read(msg.substring(1), StoreEvent.class);
                if (e.type() == StoreEvent.Type.PACKED && orders.containsKey(e.orderId())) {
                    orders.get(e.orderId()).packed = true;
                }
            }
        } catch (RuntimeException ex) {
            log.warn("Skipping bad inbox message: {}", ex.getMessage());
        }
    }

    private void cancel(String orderId) {
        DispatchOrder o = orders.get(orderId);
        if (o == null || o.status == DispatchOrder.Status.PICKED_UP || o.status == DispatchOrder.Status.DELIVERED) return;
        o.status = DispatchOrder.Status.CANCELLED;
        o.closedAt = System.currentTimeMillis();
        if (o.riderId != null) {
            Rider r = riders.get(o.riderId);
            if (r != null) {
                r.orders.remove(orderId);
                if (r.orders.isEmpty()) {
                    r.status = r.status == Rider.Status.AT_STORE ? Rider.Status.IDLE : Rider.Status.RETURNING;
                    r.idleSince = System.currentTimeMillis();
                }
            }
        }
    }

    // ------------------------------------------------------------------ assignment

    private void assignQueued(long now) {
        List<DispatchOrder> queued = orders.values().stream()
                .filter(o -> o.status == DispatchOrder.Status.QUEUED)
                .sorted(Comparator.comparingLong(o -> o.queuedAt))
                .toList();
        for (DispatchOrder o : queued) {
            Store s = stores.get(o.storeId);
            if (s == null) continue;
            List<Rider> candidates = nearbyRiders(s);
            Assigner.choose(o, s.lat(), s.lon(), candidates, orders::get, now, props.timeScale()).ifPresent(c -> {
                Rider r = c.rider();
                o.status = DispatchOrder.Status.ASSIGNED;
                o.riderId = r.id;
                o.batchedWith = c.batchedWith();
                r.orders.add(o.orderId);
                if (c.batchedWith() != null) {
                    r.batches++;
                    DispatchOrder first = orders.get(c.batchedWith());
                    if (first != null) first.batchedWith = o.orderId;
                    meters.counter("dispatch.batched").increment();
                } else if (r.status == Rider.Status.IDLE) {
                    r.status = Rider.Status.AT_STORE;
                } else {
                    r.status = Rider.Status.TO_STORE;
                }
                long eta = Geo.rideSeconds(r.lat, r.lon, s.lat(), s.lon()) + STORE_HANDOVER_SIM_SECONDS
                        + Geo.rideSeconds(s.lat(), s.lon(), o.lat, o.lon);
                o.etaAt = now + realMillis(eta);
                // `now` was taken at the start of the tick, before this order arrived from the inbox,
                // so the wait can be a few ms negative; a negative timer would abort the whole tick.
                meters.timer("dispatch.wait").record(java.time.Duration.ofMillis(Math.max(0, now - o.queuedAt)));
                emit(DispatchEvent.Type.RIDER_ASSIGNED, o, r, eta);
            });
        }
    }

    /**
     * Candidate riders come from a Redis GEOSEARCH around the store: the same spatial index
     * query a real dispatcher runs against live GPS pings.
     */
    private List<Rider> nearbyRiders(Store s) {
        var hits = redis.opsForGeo().radius(FleetStore.geoKey(s.id()),
                new Circle(new Point(s.lon(), s.lat()), new Distance(5, Metrics.KILOMETERS)));
        List<Rider> out = new ArrayList<>();
        if (hits != null) {
            hits.getContent().forEach(h -> {
                Rider r = riders.get(h.getContent().getName());
                if (r != null && r.storeId.equals(s.id())) out.add(r);
            });
        }
        return out;
    }

    // ------------------------------------------------------------------ movement

    private void move(long now, long dtMillis) {
        double simSeconds = dtMillis * props.timeScale() / 1000.0;
        double stepKm = Geo.RIDER_KMPH / Geo.ROAD_FACTOR * simSeconds / 3600.0;
        for (Rider r : riders.values()) {
            Store s = stores.get(r.storeId);
            if (s == null) continue;
            switch (r.status) {
                case IDLE -> { }
                case TO_STORE, RETURNING -> {
                    double[] p = Geo.step(r.lat, r.lon, s.lat(), s.lon(), stepKm);
                    r.lat = p[0];
                    r.lon = p[1];
                    if (Geo.km(r.lat, r.lon, s.lat(), s.lon()) < 0.01) {
                        if (r.orders.isEmpty()) {
                            r.status = Rider.Status.IDLE;
                            r.idleSince = now;
                        } else {
                            r.status = Rider.Status.AT_STORE;
                            r.orders.forEach(id -> emit(DispatchEvent.Type.ARRIVED_AT_STORE, orders.get(id), r, null));
                        }
                    }
                }
                case AT_STORE -> {
                    boolean allPacked = !r.orders.isEmpty() && r.orders.stream()
                            .map(orders::get).allMatch(o -> o != null && o.packed);
                    if (allPacked) depart(r, s, now);
                }
                case DELIVERING -> {
                    DispatchOrder o = orders.get(r.route.get(0));
                    double[] p = Geo.step(r.lat, r.lon, o.lat, o.lon, stepKm);
                    r.lat = p[0];
                    r.lon = p[1];
                    if (Geo.km(r.lat, r.lon, o.lat, o.lon) < 0.01) {
                        r.status = Rider.Status.AT_DROP;
                        r.busyUntil = now + realMillis(DROP_HANDOVER_SIM_SECONDS);
                    }
                }
                case AT_DROP -> {
                    if (now >= r.busyUntil) {
                        DispatchOrder o = orders.get(r.route.remove(0));
                        o.status = DispatchOrder.Status.DELIVERED;
                        o.closedAt = now;
                        r.orders.remove(o.orderId);
                        r.deliveries++;
                        emit(DispatchEvent.Type.DELIVERED, o, r, 0L);
                        meters.counter("dispatch.delivered").increment();
                        if (r.route.isEmpty()) {
                            r.status = Rider.Status.RETURNING;
                        } else {
                            r.status = Rider.Status.DELIVERING;
                        }
                    }
                }
            }
        }
    }

    /** All bags are packed: pick up, plan the drops nearest-first, and leave. */
    private void depart(Rider r, Store s, long now) {
        List<DispatchOrder> bags = r.orders.stream().map(orders::get).collect(Collectors.toCollection(ArrayList::new));
        List<String> route = new ArrayList<>();
        double lat = s.lat(), lon = s.lon();
        long cumulative = STORE_HANDOVER_SIM_SECONDS;
        Map<DispatchOrder, Long> etas = new LinkedHashMap<>();
        while (!bags.isEmpty()) {
            final double fLat = lat, fLon = lon;
            DispatchOrder next = bags.stream().min(Comparator.comparingDouble(o -> Geo.km(fLat, fLon, o.lat, o.lon))).orElseThrow();
            bags.remove(next);
            cumulative += Geo.rideSeconds(lat, lon, next.lat, next.lon);
            etas.put(next, cumulative);
            cumulative += DROP_HANDOVER_SIM_SECONDS;
            route.add(next.orderId);
            lat = next.lat;
            lon = next.lon;
        }
        r.route = route;
        r.status = Rider.Status.DELIVERING;
        etas.forEach((o, eta) -> {
            o.status = DispatchOrder.Status.PICKED_UP;
            o.etaAt = now + realMillis(eta);
            emit(DispatchEvent.Type.PICKED_UP, o, r, eta);
        });
    }

    // ------------------------------------------------------------------ helpers

    private void emit(DispatchEvent.Type type, DispatchOrder o, Rider r, Long etaSeconds) {
        if (o == null) return;
        DispatchEvent e = new DispatchEvent(UUID.randomUUID().toString(), type, o.orderId, o.storeId, r.id, r.name,
                o.batchedWith, etaSeconds, Instant.now());
        kafka.send(Topics.DISPATCH_EVENTS, o.orderId, json.write(e));
    }

    private long realMillis(long simSeconds) {
        return (long) (simSeconds * 1000 / props.timeScale());
    }

    private void seedFleetIfEmpty() {
        if (!riders.isEmpty()) return;
        int n = 0;
        for (Store s : stores.all().values()) {
            for (int i = 1; i <= props.ridersPerStore(); i++) {
                String id = s.id() + "-R" + i;
                Rider r = new Rider(id, NAMES[n++ % NAMES.length], s.id(), s.lat(), s.lon());
                riders.put(id, r);
            }
        }
        log.info("Seeded {} riders across {} stores", riders.size(), stores.all().size());
    }

    /** Ops action: add riders to a store (surge staffing). Applied by the leader. */
    public synchronized int addRiders(String storeId, int count) {
        if (!leader.isLeader() || riders == null) throw new IllegalStateException("Not the dispatch leader, retry");
        Store s = stores.get(storeId);
        if (s == null) throw new IllegalArgumentException("Unknown store " + storeId);
        long existing = riders.values().stream().filter(r -> r.storeId.equals(storeId)).count();
        for (int i = 1; i <= count; i++) {
            String id = storeId + "-R" + (existing + i);
            riders.put(id, new Rider(id, NAMES[(int) ((existing + i) % NAMES.length)], storeId, s.lat(), s.lon()));
        }
        return (int) existing + count;
    }

    private void purgeClosed(long now) {
        Set<String> old = new HashSet<>();
        orders.forEach((id, o) -> {
            if (o.closedAt > 0 && now - o.closedAt > 10 * 60_000) old.add(id);
        });
        old.forEach(orders::remove);
        store.deleteOrders(old);
    }
}
