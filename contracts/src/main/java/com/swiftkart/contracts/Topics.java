package com.swiftkart.contracts;

/** Kafka topics. All are keyed by order id, so every event for one order is processed in order. */
public final class Topics {
    /** Order lifecycle decided by order-service: CONFIRMED, CANCELLED. */
    public static final String ORDER_EVENTS = "order.events";
    /** Dark-store floor events from inventory-service: PACKING_STARTED, PACKED, RESERVATION_EXPIRED. */
    public static final String STORE_EVENTS = "store.events";
    /** Last-mile events from dispatch-service: RIDER_ASSIGNED, ARRIVED_AT_STORE, PICKED_UP, DELIVERED. */
    public static final String DISPATCH_EVENTS = "dispatch.events";

    private Topics() {
    }
}
