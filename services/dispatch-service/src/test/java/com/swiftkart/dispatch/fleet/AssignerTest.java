package com.swiftkart.dispatch.fleet;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AssignerTest {

    static final double STORE_LAT = 12.9352, STORE_LON = 77.6245;

    private static Rider rider(String id, Rider.Status status, double lat, double lon, long idleSince) {
        Rider r = new Rider(id, id, "BLR-KOR", lat, lon);
        r.status = status;
        r.idleSince = idleSince;
        return r;
    }

    @Test
    void prefersTheRiderWhoHasWaitedLongestAmongThoseAtTheStore() {
        long now = System.currentTimeMillis();
        Rider fresh = rider("fresh", Rider.Status.IDLE, STORE_LAT, STORE_LON, now);
        Rider waiting = rider("waiting", Rider.Status.IDLE, STORE_LAT, STORE_LON, now - 60_000); // 6 sim minutes at 6x
        DispatchOrder o = new DispatchOrder("o1", "BLR-KOR", 12.94, 77.63, 0);
        var choice = Assigner.choose(o, STORE_LAT, STORE_LON, List.of(fresh, waiting), id -> null, now, 6);
        assertThat(choice).get().extracting(c -> c.rider().id).isEqualTo("waiting");
    }

    @Test
    void returningRiderCloseToStoreBeatsNobody() {
        long now = System.currentTimeMillis();
        Rider returning = rider("r", Rider.Status.RETURNING, 12.94, 77.63, now);
        Rider busy = rider("busy", Rider.Status.DELIVERING, STORE_LAT, STORE_LON, now);
        var choice = Assigner.choose(new DispatchOrder("o1", "BLR-KOR", 12.93, 77.62, 0), STORE_LAT, STORE_LON,
                List.of(returning, busy), id -> null, now, 6);
        assertThat(choice).get().extracting(c -> c.rider().id).isEqualTo("r");
    }

    @Test
    void batchesNearbyDropsOntoARiderStillAtTheStore() {
        long now = System.currentTimeMillis();
        DispatchOrder first = new DispatchOrder("first", "BLR-KOR", 12.9450, 77.6300, 0);
        Rider atStore = rider("a", Rider.Status.AT_STORE, STORE_LAT, STORE_LON, now);
        atStore.orders.add("first");
        Rider idle = rider("idle", Rider.Status.IDLE, STORE_LAT, STORE_LON, now - 600_000);
        Map<String, DispatchOrder> orders = new HashMap<>(Map.of("first", first));

        DispatchOrder neighbour = new DispatchOrder("second", "BLR-KOR", 12.9460, 77.6310, 0); // ~150 m away
        var choice = Assigner.choose(neighbour, STORE_LAT, STORE_LON, List.of(atStore, idle), orders::get, now, 6);
        assertThat(choice).get().satisfies(c -> {
            assertThat(c.rider().id).isEqualTo("a");
            assertThat(c.batchedWith()).isEqualTo("first");
        });
    }

    @Test
    void doesNotBatchDropsInOppositeDirections() {
        long now = System.currentTimeMillis();
        DispatchOrder first = new DispatchOrder("first", "BLR-KOR", 12.955, 77.6245, 0);   // ~2.2 km north
        Rider atStore = rider("a", Rider.Status.AT_STORE, STORE_LAT, STORE_LON, now);
        atStore.orders.add("first");
        Rider idle = rider("idle", Rider.Status.IDLE, STORE_LAT, STORE_LON, now);
        DispatchOrder south = new DispatchOrder("second", "BLR-KOR", 12.915, 77.6245, 0); // ~2.2 km south
        var choice = Assigner.choose(south, STORE_LAT, STORE_LON, List.of(atStore, idle), Map.of("first", first)::get, now, 6);
        assertThat(choice).get().satisfies(c -> {
            assertThat(c.rider().id).isEqualTo("idle");
            assertThat(c.batchedWith()).isNull();
        });
    }

    @Test
    void noFreeRiderMeansTheOrderWaits() {
        Rider busy = rider("busy", Rider.Status.DELIVERING, STORE_LAT, STORE_LON, 0);
        assertThat(Assigner.choose(new DispatchOrder("o", "BLR-KOR", 12.93, 77.62, 0), STORE_LAT, STORE_LON,
                List.of(busy), id -> null, System.currentTimeMillis(), 6)).isEmpty();
    }

    @Test
    void geometry() {
        assertThat(Geo.km(12.9352, 77.6245, 12.9784, 77.6408)).isBetween(5.0, 5.3); // Koramangala to Indiranagar
        double[] p = Geo.step(0, 0, 0, 1, 0.5);
        assertThat(Geo.km(0, 0, p[0], p[1])).isBetween(0.49, 0.51);
        assertThat(Geo.rideSeconds(0, 0, 0, 0.0899)).isBetween(2480L, 2560L); // ~10 km straight = 14 km of road at 20 km/h
    }
}
