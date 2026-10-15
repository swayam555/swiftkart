package com.swiftkart.order.order;

import java.util.Set;

/**
 * Customer-visible milestones. Events can arrive out of order (a rider can be assigned while
 * the order is still being packed), so the status only ever moves forward by rank, and terminal
 * states are final.
 */
public enum OrderStatus {
    PAYMENT_PENDING(0), CONFIRMED(1), PACKING(2), PACKED(3), OUT_FOR_DELIVERY(4), DELIVERED(5),
    CANCELLED(9), PAYMENT_FAILED(9), EXPIRED(9);

    private final int rank;

    OrderStatus(int rank) {
        this.rank = rank;
    }

    public boolean isTerminal() {
        return rank == 9 || this == DELIVERED;
    }

    public boolean canAdvanceTo(OrderStatus next) {
        return !isTerminal() && next.rank > rank;
    }

    /** Customers may cancel until the rider has the bag. */
    public boolean isCancellable() {
        return Set.of(PAYMENT_PENDING, CONFIRMED, PACKING, PACKED).contains(this);
    }

    public boolean isActive() {
        return !isTerminal();
    }
}
