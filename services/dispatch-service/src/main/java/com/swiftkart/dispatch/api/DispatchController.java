package com.swiftkart.dispatch.api;

import com.swiftkart.contracts.Store;
import com.swiftkart.dispatch.config.AppProperties;
import com.swiftkart.dispatch.fleet.DispatchOrder;
import com.swiftkart.dispatch.fleet.FleetSimulator;
import com.swiftkart.dispatch.fleet.FleetStore;
import com.swiftkart.dispatch.fleet.LeaderElection;
import com.swiftkart.dispatch.fleet.Rider;
import com.swiftkart.dispatch.fleet.StoreDirectory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read endpoints work on every replica (they read the Redis snapshot); writes go to the leader. */
@RestController
@RequestMapping("/api/dispatch")
public class DispatchController {

    private final FleetStore fleet;
    private final StoreDirectory stores;
    private final LeaderElection leader;
    private final FleetSimulator simulator;
    private final AppProperties props;

    public DispatchController(FleetStore fleet, StoreDirectory stores, LeaderElection leader, FleetSimulator simulator,
                              AppProperties props) {
        this.fleet = fleet;
        this.stores = stores;
        this.leader = leader;
        this.simulator = simulator;
        this.props = props;
    }

    @GetMapping("/riders")
    public Collection<Rider> riders(@RequestParam(required = false) String storeId) {
        return fleet.loadRiders().values().stream().filter(r -> storeId == null || r.storeId.equals(storeId)).toList();
    }

    /** Live position of the rider carrying an order, for the customer's tracking map. */
    @GetMapping("/orders/{orderId}/track")
    public ResponseEntity<Map<String, Object>> track(@PathVariable String orderId) {
        DispatchOrder o = fleet.order(orderId);
        // Right after payment the order's Kafka event may not have reached dispatch yet: that's a
        // normal state for the tracking page to poll through, not an error.
        if (o == null) return ResponseEntity.ok(Map.of("orderId", orderId, "status", "PENDING"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", orderId);
        body.put("status", o.status);
        body.put("packed", o.packed);
        body.put("drop", Map.of("lat", o.lat, "lon", o.lon));
        Store s = stores.get(o.storeId);
        if (s != null) body.put("store", Map.of("lat", s.lat(), "lon", s.lon(), "name", s.name()));
        if (o.etaAt > 0) {
            double minutes = Math.max(0, (o.etaAt - System.currentTimeMillis()) * props.timeScale() / 60_000.0);
            body.put("etaMinutes", Math.round(minutes * 10) / 10.0);
        }
        if (o.riderId != null) {
            Rider r = fleet.rider(o.riderId);
            if (r != null) {
                body.put("rider", Map.of("id", r.id, "name", r.name, "lat", r.lat, "lon", r.lon,
                        "status", r.status, "carrying", r.orders.size()));
            }
        }
        body.put("batchedWith", o.batchedWith);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Collection<Rider> riders = fleet.loadRiders().values();
        Collection<DispatchOrder> orders = fleet.loadOrders().values();
        Map<Rider.Status, Long> byStatus = new EnumMap<>(Rider.Status.class);
        for (Rider.Status s : Rider.Status.values()) byStatus.put(s, 0L);
        riders.forEach(r -> byStatus.merge(r.status, 1L, Long::sum));
        long busy = riders.stream().filter(r -> r.status != Rider.Status.IDLE).count();
        long queued = orders.stream().filter(o -> o.status == DispatchOrder.Status.QUEUED).count();
        long oldestWaitMs = orders.stream().filter(o -> o.status == DispatchOrder.Status.QUEUED)
                .mapToLong(o -> System.currentTimeMillis() - o.queuedAt).max().orElse(0);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("riders", riders.size());
        body.put("ridersByStatus", byStatus);
        body.put("utilization", riders.isEmpty() ? 0 : Math.round(1000.0 * busy / riders.size()) / 10.0);
        body.put("ordersWaitingForRider", queued);
        body.put("oldestWaitMinutes", Math.round(oldestWaitMs * props.timeScale() / 6_000.0) / 10.0);
        body.put("deliveriesTotal", riders.stream().mapToLong(r -> r.deliveries).sum());
        body.put("batchedTrips", riders.stream().mapToLong(r -> r.batches).sum());
        body.put("leader", leader.isLeader() ? "this instance" : "another instance");
        body.put("timeScale", props.timeScale());
        return body;
    }

    @PostMapping("/ops/riders")
    public Map<String, Object> addRiders(@RequestBody AddRiders req) {
        int total = simulator.addRiders(req.storeId(), Math.max(1, Math.min(req.count(), 20)));
        return Map.of("storeId", req.storeId(), "riders", total);
    }

    @GetMapping("/stores")
    public List<Store> stores() {
        return List.copyOf(stores.all().values());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<Map<String, String>> bad(RuntimeException e) {
        return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
    }

    public record AddRiders(String storeId, int count) {
    }
}
