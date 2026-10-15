package com.swiftkart.dispatch.fleet;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Function;

/**
 * Picks the rider for an order. Pure logic with no I/O, so it is unit-tested directly.
 *
 * <ol>
 *   <li><b>Batch</b> if a rider is already headed to this store for one order whose drop is
 *       close to this one, and the detour costs at most {@link #MAX_BATCH_DETOUR_SECONDS}. One trip,
 *       two deliveries: lower cost per order, more earnings per hour for the rider.</li>
 *   <li>Otherwise take a <b>free rider</b> (idle at the store, or riding back to it), scoring
 *       time-to-store minus a fairness bonus for how long an idle rider has been waiting, so
 *       work is spread across the fleet instead of always going to whoever stands closest.</li>
 * </ol>
 */
public final class Assigner {

    static final double MAX_BATCH_DROP_GAP_KM = 1.2;
    static final long MAX_BATCH_DETOUR_SECONDS = 240;
    static final double FAIRNESS_SECONDS_PER_IDLE_MINUTE = 15;

    private Assigner() {
    }

    public static Optional<Choice> choose(DispatchOrder order, double storeLat, double storeLon,
                                          Collection<Rider> candidates, Function<String, DispatchOrder> lookup,
                                          long nowMillis, double timeScale) {
        Optional<Choice> batch = candidates.stream()
                .filter(Rider::canBatch)
                .map(r -> {
                    DispatchOrder first = lookup.apply(r.orders.get(0));
                    if (first == null) return null;
                    double gap = Geo.km(first.lat, first.lon, order.lat, order.lon);
                    if (gap > MAX_BATCH_DROP_GAP_KM) return null;
                    long solo = Geo.rideSeconds(storeLat, storeLon, first.lat, first.lon);
                    long combined = solo + Geo.rideSeconds(first.lat, first.lon, order.lat, order.lon);
                    long detour = combined - solo;
                    return detour <= MAX_BATCH_DETOUR_SECONDS ? new Choice(r, first.orderId, detour) : null;
                })
                .filter(java.util.Objects::nonNull)
                .min(Comparator.comparingDouble(Choice::score));
        if (batch.isPresent()) return batch;

        return candidates.stream()
                .filter(Rider::isFree)
                .map(r -> {
                    long toStore = Geo.rideSeconds(r.lat, r.lon, storeLat, storeLon);
                    double idleMinutes = r.status == Rider.Status.IDLE
                            ? (nowMillis - r.idleSince) * timeScale / 60_000.0 : 0;
                    double score = toStore - FAIRNESS_SECONDS_PER_IDLE_MINUTE * Math.min(idleMinutes, 20);
                    return new Choice(r, null, score);
                })
                .min(Comparator.comparingDouble(Choice::score));
    }

    public record Choice(Rider rider, String batchedWith, double score) {
    }
}
