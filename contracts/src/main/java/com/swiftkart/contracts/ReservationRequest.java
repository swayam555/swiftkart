package com.swiftkart.contracts;

import java.util.List;

/**
 * @param mode ATOMIC is the production path (one Lua script). NAIVE is a deliberately broken
 *             read-then-write implementation, kept only so the flash-sale demo can show what goes
 *             wrong without atomicity.
 */
public record ReservationRequest(String orderId, String storeId, List<Item> items, Mode mode) {

    public record Item(String sku, int qty) {
    }

    public enum Mode { ATOMIC, NAIVE }
}
