package com.swiftkart.contracts;

import java.time.Instant;
import java.util.List;

public record ReservationResult(boolean reserved, String orderId, List<String> unavailableSkus, Instant expiresAt) {
}
