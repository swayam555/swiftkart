package com.swiftkart.order.order;

import java.time.Duration;

/**
 * The delivery promise shown at checkout: picking time, plus the queue in front of this order,
 * plus the ride. Promise too little and the order arrives late; promise too much and the
 * customer opens a competitor's app. Ops tracks the on-time rate against this number.
 */
public final class EtaEstimator {

    static final double ROAD_FACTOR = 1.4;     // roads are longer than straight lines
    static final double RIDER_KMPH = 20;       // city two-wheeler average
    static final int PICKERS_PER_STORE = 3;

    private EtaEstimator() {
    }

    public static Duration estimate(int units, double distanceKm, long ordersAhead) {
        Duration pick = Duration.ofSeconds(60 + 12L * Math.min(units, 20));
        Duration queue = Duration.ofSeconds((long) (ordersAhead / (double) PICKERS_PER_STORE * 90));
        Duration handover = Duration.ofSeconds(60);
        Duration ride = Duration.ofSeconds(Math.round(distanceKm * ROAD_FACTOR / RIDER_KMPH * 3600));
        return pick.plus(queue).plus(handover).plus(ride);
    }

    /** Rounded up to whole minutes, with a small buffer: "Delivery in 9 minutes". */
    public static int promisedMinutes(Duration estimate) {
        return (int) Math.ceil(estimate.toSeconds() * 1.1 / 60.0);
    }
}
