package com.swiftkart.contracts;

import java.time.Instant;

public record StoreEvent(String eventId, Type type, String orderId, String storeId, String detail, Instant at) {

    public enum Type { PACKING_STARTED, PACKED, RESERVATION_EXPIRED }
}
