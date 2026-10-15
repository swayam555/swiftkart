package com.swiftkart.contracts;

import java.time.Instant;
import java.util.List;

public record OrderEvent(
        String eventId,
        Type type,
        String orderId,
        String storeId,
        String customerId,
        List<OrderLine> lines,
        double customerLat,
        double customerLon,
        Instant promisedBy,
        String reason,
        Instant at) {

    public enum Type { CONFIRMED, CANCELLED }
}
