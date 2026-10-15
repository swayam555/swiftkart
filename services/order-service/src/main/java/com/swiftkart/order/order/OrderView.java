package com.swiftkart.order.order;

import com.swiftkart.contracts.OrderLine;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * API shape of an order. Minutes are on the simulated clock so the UI reads naturally
 * ("arriving in 4 min") even though the demo runs faster than real time.
 */
public record OrderView(
        String id, String status, String storeId, String customerId, String customerName,
        List<OrderLine> lines, int itemCount, BigDecimal itemTotal, BigDecimal deliveryFee, BigDecimal total,
        double customerLat, double customerLon, String address, double distanceKm,
        int promisedMinutes, Instant promisedBy, Instant expectedBy, Double minutesRemaining, Double minutesElapsed,
        Double deliveredInMinutes, Boolean onTime, Instant reservationExpiresAt,
        String riderId, String riderName, String batchedWith, String cancelReason, String source,
        Instant placedAt, Instant paidAt, Instant packingAt, Instant packedAt, Instant riderAssignedAt,
        Instant pickedUpAt, Instant deliveredAt, List<TimelineRepository.Entry> timeline) {
}
