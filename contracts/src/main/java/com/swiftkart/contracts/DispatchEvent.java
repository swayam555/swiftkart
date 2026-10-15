package com.swiftkart.contracts;

import java.time.Instant;

/**
 * @param batchedWith set when the rider carries this order together with another one
 * @param etaSeconds  simulated seconds until the customer receives the order
 */
public record DispatchEvent(
        String eventId,
        Type type,
        String orderId,
        String storeId,
        String riderId,
        String riderName,
        String batchedWith,
        Long etaSeconds,
        Instant at) {

    public enum Type { RIDER_ASSIGNED, ARRIVED_AT_STORE, PICKED_UP, DELIVERED }
}
